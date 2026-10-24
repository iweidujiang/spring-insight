package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.ai.InsightAiExplainService;
import io.github.iweidujiang.springinsight.server.anomaly.AnomalyDetectionResult;
import io.github.iweidujiang.springinsight.server.anomaly.AnomalyDetector;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettings;
import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettingsService;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时扫描服务错误 / p95 延迟窗口，超阈值则推送 Webhook 和/或邮件；可选附带 AI 短解读。
 * <p>
 * 是否启用以 {@link InsightRuntimeSettingsService#effectiveAlert()} 为准（控制台可改，无需重启）。
 * 静默时段内跳过推送（仍跟踪活跃告警以便恢复判定）。
 * 未恢复超过配置时长时可向指定渠道发送一次升级通知。
 * </p>
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@Component
public class InsightAlertScheduler {

    /** 扫描间隔；窗口与冷却仍由配置决定 */
    static final long SCAN_INTERVAL_MS = 60_000L;

    private final InsightRuntimeSettingsService settingsService;
    private final TraceSpanPersistenceService persistenceService;
    private final List<InsightAlertChannelSender> channelSenders;
    private final InsightAlertMetrics metrics;
    private final InsightAiExplainService aiExplainService;
    private final AnomalyDetector anomalyDetector;

    /** 服务名 → 上次成功推送的 epoch 毫秒 */
    private final ConcurrentHashMap<String, Long> lastFiredAt = new ConcurrentHashMap<>();
    /** 服务名 → 活跃告警（未恢复）状态 */
    private final ConcurrentHashMap<String, AlertEscalationState> activeAlerts = new ConcurrentHashMap<>();
    private final AtomicBoolean warnedNotReady = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler;

    /**
     * @param settingsService     运行时设置
     * @param persistenceService  聚合
     * @param channelSenders      已注册的推送渠道
     * @param metrics             结果计数
     * @param aiExplainService    可选告警 AI 解读
     * @param anomalyDetector     无阈值异常检测
     */
    public InsightAlertScheduler(InsightRuntimeSettingsService settingsService,
                                 TraceSpanPersistenceService persistenceService,
                                 List<InsightAlertChannelSender> channelSenders,
                                 InsightAlertMetrics metrics,
                                 InsightAiExplainService aiExplainService,
                                 AnomalyDetector anomalyDetector) {
        this.settingsService = settingsService;
        this.persistenceService = persistenceService;
        this.channelSenders = channelSenders == null ? List.of() : List.copyOf(channelSenders);
        this.metrics = metrics;
        this.aiExplainService = aiExplainService;
        this.anomalyDetector = anomalyDetector;
    }

    /**
     * 始终启动调度线程；每轮读取运行时开关。
     */
    @PostConstruct
    void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "insight-alert");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::scanSafe, SCAN_INTERVAL_MS, SCAN_INTERVAL_MS, TimeUnit.MILLISECONDS);
        InsightServerAlertProperties p = settingsService.effectiveAlert();
        log.info("[告警] 调度已启动（当前 enabled={}；可在控制台「设置」页开关，无需重启）", p.isEnabled());
    }

    /**
     * 停止调度线程。
     */
    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 执行一轮扫描；异常只记日志。
     */
    void scanSafe() {
        try {
            scan();
        } catch (Exception e) {
            log.warn("[告警] 扫描失败: {}", e.getMessage());
        }
    }

    /**
     * 按窗口聚合指标；先写入异常检测滑动窗，再评估 / 跟踪活跃告警并按需推送与升级。
     * <p>
     * 异常检测在「告警已启用」时即运行，不依赖通道是否配齐；无规则命中时仍会留下异常标记供 UI 读取。
     * </p>
     */
    void scan() {
        InsightServerAlertProperties properties = settingsService.effectiveAlert();
        if (!properties.isEnabled()) {
            return;
        }

        int windowMinutes = properties.getWindowMinutes() > 0 ? properties.getWindowMinutes() : 15;
        long since = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES).toEpochMilli();
        long now = System.currentTimeMillis();
        String at = Instant.ofEpochMilli(now).truncatedTo(ChronoUnit.SECONDS).toString();
        List<Map<String, Object>> rows = persistenceService.getServiceLatencySummariesSince(since, Integer.MAX_VALUE);
        sampleAnomalies(rows);

        if (!hasSupportedCondition(properties)) {
            log.warn("[告警] 不支持的 metric={}，仅接受 error_rate / error_count / latency_p95 / span_count",
                    properties.normalizedMetric());
            return;
        }

        boolean deliverOk = canDeliver(properties);
        if (!deliverOk) {
            if (warnedNotReady.compareAndSet(false, true)) {
                log.warn("[告警] 已启用但未配置可用通道（webhook-url、邮件、钉钉、飞书或企业微信机器人）");
            }
        } else {
            warnedNotReady.set(false);
        }

        ZonedDateTime nowZdt = ZonedDateTime.now(InsightAlertEvaluator.resolveZone(properties.getQuietHoursZone()));
        boolean quiet = InsightAlertEvaluator.inQuietHours(properties, nowZdt);
        if (quiet) {
            log.debug("[告警] 静默时段内跳过推送（仍跟踪活跃告警）");
            metrics.record("quiet");
        }

        boolean allowNotify = deliverOk && !quiet;
        scanServices(properties, rows, now, at, allowNotify);
    }

    /**
     * 复用告警扫描同一批服务指标，写入异常检测窗口。
     *
     * @param rows {@link TraceSpanPersistenceService#getServiceLatencySummariesSince} 结果
     */
    private void sampleAnomalies(List<Map<String, Object>> rows) {
        if (rows == null || anomalyDetector == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            String service = String.valueOf(row.get("service_name"));
            double errorRate = number(row.get("error_rate"));
            long errorCount = (long) number(row.get("error_count"));
            long p95 = (long) number(row.get("p95_ms"));
            long spanCount = (long) number(row.get("span_count"));
            anomalyDetector.detect("error_rate", service, errorRate);
            anomalyDetector.detect("error_count", service, errorCount);
            anomalyDetector.detect("latency_p95", service, p95);
            anomalyDetector.detect("span_count", service, spanCount);
        }
    }

    /**
     * @param properties 告警配置
     * @return 生效条件里是否至少有一条已知指标
     */
    private static boolean hasSupportedCondition(InsightServerAlertProperties properties) {
        for (AlertCondition condition : properties.effectiveConditions()) {
            String metric = condition.getMetric() == null ? "" : condition.getMetric().trim().toLowerCase();
            if (InsightAlertEvaluator.supportedMetricName(metric)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 同一服务上同时看错误率、错误次数、p95 和 Span 数，全部条件成立才推送。
     * <p>
     * 无论是否允许推送，都会更新活跃告警表：触发则登记 / 续期，未触发则清除（视为恢复）。
     * </p>
     *
     * @param rows        本轮已聚合的服务指标（与异常采样共用）
     * @param allowNotify 是否允许首次推送与升级通知
     */
    private void scanServices(InsightServerAlertProperties properties,
                              List<Map<String, Object>> rows,
                              long now,
                              String at,
                              boolean allowNotify) {
        if (rows == null) {
            rows = List.of();
        }
        Set<String> stillFiring = new HashSet<>();
        for (Map<String, Object> row : rows) {
            String service = String.valueOf(row.get("service_name"));
            double errorRate = number(row.get("error_rate"));
            long errorCount = (long) number(row.get("error_count"));
            long p95 = (long) number(row.get("p95_ms"));
            long spanCount = (long) number(row.get("span_count"));
            AlertEvaluation evaluation = InsightAlertEvaluator.evaluate(
                    properties, errorRate, errorCount, p95, spanCount);
            if (!evaluation.fired() || evaluation.matched().isEmpty()) {
                continue;
            }
            stillFiring.add(service);
            AlertEscalationState state = activeAlerts.compute(service, (k, prev) -> {
                if (prev == null) {
                    return AlertEscalationState.open(now);
                }
                return prev;
            });
            if (allowNotify) {
                tryDeliver(properties, service, evaluation, now, at);
                maybeEscalate(properties, service, evaluation, state, now, at);
            }
        }
        for (String service : new ArrayList<>(activeAlerts.keySet())) {
            if (!stillFiring.contains(service)) {
                activeAlerts.remove(service);
                log.debug("[告警] 已恢复，清除活跃状态: service={}", service);
            }
        }
    }

    /**
     * 未恢复且超过升级时限时，向配置渠道发送一次升级通知。
     *
     * @param properties 告警配置
     * @param service    服务
     * @param evaluation 本轮判定
     * @param state      活跃状态
     * @param now        当前 ms
     * @param at         ISO 时间
     */
    private void maybeEscalate(InsightServerAlertProperties properties,
                               String service,
                               AlertEvaluation evaluation,
                               AlertEscalationState state,
                               long now,
                               String at) {
        InsightRuntimeSettings.AlertEscalation esc = effectiveEscalation();
        if (esc == null || !esc.isEnabled()) {
            return;
        }
        if (state == null || !state.shouldEscalate(now, esc.getAfterMinutes())) {
            return;
        }
        String channelId = esc.getChannelId() == null ? "" : esc.getChannelId().trim();
        if (channelId.isEmpty()) {
            log.warn("[告警] 升级已启用但未配置 channelId: service={}", service);
            return;
        }
        InsightRuntimeSettings.ChannelConfig channel = findChannel(channelId);
        if (channel == null || !channel.isEnabled()) {
            log.warn("[告警] 升级渠道不可用: service={}, channelId={}", service, channelId);
            return;
        }
        String type = channel.getType() == null ? "" : channel.getType().trim().toLowerCase();
        if (!channelReady(channel, type)) {
            log.warn("[告警] 升级渠道未配齐: service={}, channelId={}, type={}", service, channelId, type);
            return;
        }

        AlertEvaluation.MatchedCondition first = evaluation.matched().get(0);
        double value = first.value();
        long durationMinutes = state.durationMinutes(now);
        Map<String, Object> payload = InsightAlertEvaluator.payload(properties, service, value, at);
        payload.put("metric", first.condition().getMetric());
        payload.put("threshold", first.condition().getThreshold());
        payload.put("matchedConditions", matchedConditions(evaluation));
        payload.put("type", "escalation");
        payload.put("escalation", true);
        payload.put("durationMinutes", durationMinutes);
        payload.put("message", "告警升级：服务 " + service + " 已持续 "
                + durationMinutes + " 分钟未恢复（阈值 "
                + esc.getAfterMinutes() + " 分钟）");
        attachAnomaly(payload, service, first.condition().getMetric());

        boolean ok = dispatch(type, new AlertPayload(payload), channelConfig(properties, channel, type));
        metrics.record(type, ok ? "success" : "failure");
        if (ok) {
            activeAlerts.put(service, state.markEscalated());
            metrics.record("escalation");
            log.info("[告警] 已升级通知: service={}, durationMinutes={}, channelId={}",
                    service, durationMinutes, channelId);
        } else {
            log.warn("[告警] 升级通知发送失败: service={}, channelId={}", service, channelId);
        }
    }

    /**
     * @return 当前升级配置；未配置时返回默认关闭实例
     */
    private InsightRuntimeSettings.AlertEscalation effectiveEscalation() {
        InsightRuntimeSettings snap = settingsService.snapshot();
        if (snap == null || snap.getAlert() == null || snap.getAlert().getEscalation() == null) {
            return new InsightRuntimeSettings.AlertEscalation();
        }
        return snap.getAlert().getEscalation();
    }

    /**
     * @param channelId 渠道 id
     * @return 匹配渠道；找不到为 null
     */
    private InsightRuntimeSettings.ChannelConfig findChannel(String channelId) {
        for (InsightRuntimeSettings.ChannelConfig channel : settingsService.effectiveChannels()) {
            if (channel != null && channelId.equals(channel.getId())) {
                return channel;
            }
        }
        return null;
    }

    /**
     * 测试用：当前活跃告警状态。
     *
     * @param service 服务名
     * @return 状态；无则 null
     */
    AlertEscalationState activeAlert(String service) {
        return activeAlerts.get(service);
    }

    /**
     * 测试用：注入活跃告警（模拟持续未恢复）。
     *
     * @param service        服务
     * @param firstFiredAtMs 首次触发
     * @param escalated      是否已升级
     */
    void putActiveAlert(String service, long firstFiredAtMs, boolean escalated) {
        activeAlerts.put(service, new AlertEscalationState(firstFiredAtMs, escalated));
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    /**
     * @param evaluation 已触发的判定
     * @return 写入告警体的成立条件
     */
    private static List<Map<String, Object>> matchedConditions(AlertEvaluation evaluation) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AlertEvaluation.MatchedCondition matched : evaluation.matched()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("metric", matched.condition().getMetric());
            row.put("operator", matched.condition().getOperator());
            row.put("threshold", matched.condition().getThreshold());
            row.put("value", matched.value());
            rows.add(row);
        }
        return rows;
    }

    private void tryDeliver(InsightServerAlertProperties properties,
                            String service,
                            AlertEvaluation evaluation,
                            long now,
                            String at) {
        Long fired = lastFiredAt.get(service);
        if (InsightAlertEvaluator.inCooldown(fired == null ? 0L : fired, properties.getCooldownMinutes(), now)) {
            metrics.record("cooldown");
            log.debug("[告警] 冷却中跳过: service={}", service);
            return;
        }
        AlertEvaluation.MatchedCondition first = evaluation.matched().get(0);
        double value = first.value();
        Map<String, Object> payload = InsightAlertEvaluator.payload(properties, service, value, at);
        payload.put("metric", first.condition().getMetric());
        payload.put("threshold", first.condition().getThreshold());
        payload.put("matchedConditions", matchedConditions(evaluation));
        attachAnomaly(payload, service, first.condition().getMetric());
        attachAiIfNeeded(payload, properties, service, value);
        boolean delivered = deliver(properties, payload);
        if (delivered) {
            lastFiredAt.put(service, now);
            metrics.record("success");
            log.info("[告警] 已推送: service={}, metric={}, value={}, aiAttached={}",
                    service, first.condition().getMetric(), value, payload.getOrDefault("aiAttached", false));
        } else {
            metrics.record("failure");
        }
    }

    /**
     * 把本轮异常检测结果挂到告警体（无异常或样本不足也会写入，便于对账）。
     *
     * @param payload 告警体
     * @param service 服务
     * @param metric  触发指标
     */
    private void attachAnomaly(Map<String, Object> payload, String service, String metric) {
        if (anomalyDetector == null) {
            return;
        }
        AnomalyDetectionResult result = anomalyDetector.latest(metric, service);
        if (result == null) {
            result = anomalyDetector.latestAnomalyForService(service);
        }
        if (result != null) {
            payload.put("anomaly", result.toMap());
        }
    }

    /**
     * 若 AI「告警附带解读」开启，则向 payload 合并短解读字段（失败不阻断推送）。
     *
     * @param payload    告警体
     * @param properties 告警配置
     * @param service    服务名
     * @param value      指标值
     */
    private void attachAiIfNeeded(Map<String, Object> payload,
                                  InsightServerAlertProperties properties,
                                  String service,
                                  double value) {
        try {
            Map<String, Object> ai = aiExplainService.explainForAlert(
                    service,
                    properties.normalizedMetric(),
                    value,
                    properties.getThreshold(),
                    properties.getWindowMinutes() > 0 ? properties.getWindowMinutes() : 15);
            if (ai == null || Boolean.TRUE.equals(ai.get("skipped"))) {
                return;
            }
            copyAiField(payload, ai, "aiAttached");
            copyAiField(payload, ai, "aiDegraded");
            copyAiField(payload, ai, "aiSummary");
            copyAiField(payload, ai, "aiSuggestions");
            copyAiField(payload, ai, "aiModel");
        } catch (Exception e) {
            log.warn("[告警] 附带 AI 解读异常（仍推送指标）: service={}, error={}", service, e.getMessage());
        }
    }

    private static void copyAiField(Map<String, Object> target, Map<String, Object> ai, String key) {
        if (ai.containsKey(key) && ai.get(key) != null) {
            target.put(key, ai.get(key));
        }
    }

    /**
     * 有已保存渠道时只看渠道是否齐；否则沿用 Webhook、邮件和机器人字段。
     *
     * @param properties 当前生效告警配置
     * @return 是否至少有一条可投递通道
     */
    private boolean canDeliver(InsightServerAlertProperties properties) {
        List<InsightRuntimeSettings.ChannelConfig> channels = settingsService.effectiveChannels();
        if (channels != null && !channels.isEmpty()) {
            for (InsightRuntimeSettings.ChannelConfig channel : channels) {
                if (channel == null || !channel.isEnabled()) {
                    continue;
                }
                String type = channel.getType() == null ? "" : channel.getType().trim().toLowerCase();
                if (channelReady(channel, type)) {
                    return true;
                }
            }
            return false;
        }
        return properties.isSendReady();
    }

    /**
     * @param properties 当前生效告警配置
     * @param payload    告警体
     * @return 任一已配置通道成功即为 true
     */
    private boolean deliver(InsightServerAlertProperties properties, Map<String, Object> payload) {
        List<InsightRuntimeSettings.ChannelConfig> channels = settingsService.effectiveChannels();
        if (channels != null && !channels.isEmpty()) {
            return deliverChannels(properties, payload, channels);
        }
        AlertPayload alertPayload = new AlertPayload(payload);
        ChannelConfig config = new ChannelConfig(properties.normalizedWebhookUrl(), properties);
        boolean anySuccess = false;
        boolean attempted = false;
        if (properties.isWebhookSendReady()) {
            attempted = true;
            boolean ok = dispatch("webhook", alertPayload, config);
            metrics.record("webhook", ok ? "success" : "failure");
            anySuccess = anySuccess || ok;
        }
        if (properties.isEmailSendReady()) {
            attempted = true;
            boolean ok = dispatch("email", alertPayload, config);
            metrics.record("email", ok ? "success" : "failure");
            anySuccess = anySuccess || ok;
        }
        if (properties.isDingtalkSendReady()) {
            attempted = true;
            InsightServerAlertProperties.Dingtalk dingtalk = properties.getDingtalk();
            ChannelConfig dingtalkConfig = new ChannelConfig(
                    dingtalk.getWebhookUrl().trim(), properties, dingtalk.getSecret().trim());
            boolean ok = dispatch("dingtalk", alertPayload, dingtalkConfig);
            metrics.record("dingtalk", ok ? "success" : "failure");
            anySuccess = anySuccess || ok;
        }
        if (properties.isFeishuSendReady()) {
            attempted = true;
            InsightServerAlertProperties.Feishu feishu = properties.getFeishu();
            String secret = feishu.getSecret() == null ? "" : feishu.getSecret().trim();
            ChannelConfig feishuConfig = new ChannelConfig(feishu.getWebhookUrl().trim(), properties, secret);
            boolean ok = dispatch("feishu", alertPayload, feishuConfig);
            metrics.record("feishu", ok ? "success" : "failure");
            anySuccess = anySuccess || ok;
        }
        if (properties.isWecomSendReady()) {
            attempted = true;
            InsightServerAlertProperties.Wecom wecom = properties.getWecom();
            ChannelConfig wecomConfig = new ChannelConfig(wecom.getWebhookUrl().trim(), properties, "");
            boolean ok = dispatch("wecom", alertPayload, wecomConfig);
            metrics.record("wecom", ok ? "success" : "failure");
            anySuccess = anySuccess || ok;
        }
        return attempted && anySuccess;
    }

    /**
     * 按已保存的渠道列表投递。未启用或地址不齐的渠道跳过。
     *
     * @param properties 扫描用的告警配置（指标仍来自这里）
     * @param payload    告警体
     * @param channels   渠道列表
     * @return 任一渠道成功
     */
    private boolean deliverChannels(InsightServerAlertProperties properties,
                                    Map<String, Object> payload,
                                    List<InsightRuntimeSettings.ChannelConfig> channels) {
        String escalationChannelId = "";
        InsightRuntimeSettings.AlertEscalation esc = effectiveEscalation();
        if (esc != null && esc.isEnabled() && esc.getChannelId() != null) {
            escalationChannelId = esc.getChannelId().trim();
        }
        AlertPayload alertPayload = new AlertPayload(payload);
        boolean anySuccess = false;
        boolean attempted = false;
        for (InsightRuntimeSettings.ChannelConfig channel : channels) {
            if (channel == null || !channel.isEnabled()) {
                continue;
            }
            // 升级专用渠道不参与首次推送
            if (!escalationChannelId.isEmpty() && escalationChannelId.equals(channel.getId())) {
                continue;
            }
            String type = channel.getType() == null ? "" : channel.getType().trim().toLowerCase();
            if (!channelReady(channel, type)) {
                continue;
            }
            attempted = true;
            boolean ok = dispatch(type, alertPayload, channelConfig(properties, channel, type));
            metrics.record(type, ok ? "success" : "failure");
            anySuccess = anySuccess || ok;
        }
        return attempted && anySuccess;
    }

    /**
     * @param channel 渠道
     * @param type    小写类型
     * @return 必填项是否齐全
     */
    private static boolean channelReady(InsightRuntimeSettings.ChannelConfig channel, String type) {
        return switch (type) {
            case "email" -> hasText(channel.getSmtpHost()) && hasText(channel.getSmtpFrom()) && hasText(channel.getSmtpTo());
            case "dingtalk" -> hasText(channel.getWebhookUrl()) && hasText(channel.getSecret());
            case "webhook", "feishu", "wecom" -> hasText(channel.getWebhookUrl());
            default -> false;
        };
    }

    /**
     * 把一条渠道收成发送器使用的配置。邮件字段写进临时的告警配置，避免改到正在扫描的对象。
     */
    public static ChannelConfig channelConfig(InsightServerAlertProperties properties,
                                               InsightRuntimeSettings.ChannelConfig channel,
                                               String type) {
        InsightServerAlertProperties carrier = new InsightServerAlertProperties();
        carrier.setEnabled(true);
        if ("email".equals(type)) {
            InsightServerAlertProperties.Email email = new InsightServerAlertProperties.Email();
            email.setEnabled(true);
            email.setHost(channel.getSmtpHost());
            email.setPort(channel.getSmtpPort() > 0 ? channel.getSmtpPort() : 587);
            email.setUsername(channel.getSmtpUsername());
            email.setPassword(channel.getSmtpPassword());
            email.setFrom(channel.getSmtpFrom());
            email.setTo(channel.getSmtpTo());
            email.setStartTls(channel.isStartTls());
            email.setSsl(channel.isSsl());
            carrier.setEmail(email);
        }
        String serverUrl = channel.getServerUrl() == null ? "" : channel.getServerUrl();
        if ("dingtalk".equals(type)) {
            InsightServerAlertProperties.Dingtalk dingtalk = new InsightServerAlertProperties.Dingtalk();
            dingtalk.setServerUrl(serverUrl);
            carrier.setDingtalk(dingtalk);
        } else if ("feishu".equals(type)) {
            InsightServerAlertProperties.Feishu feishu = new InsightServerAlertProperties.Feishu();
            feishu.setServerUrl(serverUrl);
            carrier.setFeishu(feishu);
        } else if ("wecom".equals(type)) {
            InsightServerAlertProperties.Wecom wecom = new InsightServerAlertProperties.Wecom();
            wecom.setServerUrl(serverUrl);
            carrier.setWecom(wecom);
        } else if (properties != null) {
            carrier.setDingtalk(properties.getDingtalk());
            carrier.setFeishu(properties.getFeishu());
            carrier.setWecom(properties.getWecom());
        }
        String secret = channel.getSecret() == null ? "" : channel.getSecret();
        String token = channel.getToken() == null ? "" : channel.getToken();
        String url = channel.getWebhookUrl() == null ? "" : channel.getWebhookUrl().trim();
        return new ChannelConfig(url, carrier, secret, token);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean dispatch(String channelType, AlertPayload payload, ChannelConfig config) {
        InsightAlertChannelSender sender = null;
        for (InsightAlertChannelSender candidate : channelSenders) {
            if (channelType.equals(candidate.channelType())) {
                sender = candidate;
                break;
            }
        }
        if (sender == null) {
            log.warn("[告警] 未注册渠道: {}", channelType);
            return false;
        }
        try {
            sender.send(payload, config);
            return true;
        } catch (Exception e) {
            log.warn("[告警] {} 发送失败: {}", channelType, e.getMessage());
            return false;
        }
    }
}
