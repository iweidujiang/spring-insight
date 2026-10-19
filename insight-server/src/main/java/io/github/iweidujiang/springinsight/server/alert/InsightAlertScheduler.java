package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.ai.InsightAiExplainService;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettingsService;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时扫描服务错误 / p95 延迟窗口，超阈值则推送 Webhook 和/或邮件；可选附带 AI 短解读。
 * <p>
 * 是否启用以 {@link InsightRuntimeSettingsService#effectiveAlert()} 为准（控制台可改，无需重启）。
 * 静默时段内跳过推送（仍可扫描）。
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

    /** 服务名 → 上次成功推送的 epoch 毫秒 */
    private final ConcurrentHashMap<String, Long> lastFiredAt = new ConcurrentHashMap<>();
    private final AtomicBoolean warnedNotReady = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler;

    /**
     * @param settingsService     运行时设置
     * @param persistenceService  聚合
     * @param channelSenders      已注册的推送渠道
     * @param metrics             结果计数
     * @param aiExplainService    可选告警 AI 解读
     */
    public InsightAlertScheduler(InsightRuntimeSettingsService settingsService,
                                 TraceSpanPersistenceService persistenceService,
                                 List<InsightAlertChannelSender> channelSenders,
                                 InsightAlertMetrics metrics,
                                 InsightAiExplainService aiExplainService) {
        this.settingsService = settingsService;
        this.persistenceService = persistenceService;
        this.channelSenders = channelSenders == null ? List.of() : List.copyOf(channelSenders);
        this.metrics = metrics;
        this.aiExplainService = aiExplainService;
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
     * 按窗口聚合指标，对超阈值且不在冷却期的服务推送已配置通道。
     */
    void scan() {
        InsightServerAlertProperties properties = settingsService.effectiveAlert();
        if (!properties.isEnabled()) {
            return;
        }
        if (!properties.isSendReady()) {
            if (warnedNotReady.compareAndSet(false, true)) {
                log.warn("[告警] 已启用但未配置可用通道（webhook-url、邮件、钉钉或飞书机器人）");
            }
            return;
        }
        warnedNotReady.set(false);
        if (!InsightAlertEvaluator.supportedMetric(properties)) {
            log.warn("[告警] 不支持的 metric={}，仅接受 error_rate / error_count / latency_p95",
                    properties.normalizedMetric());
            return;
        }

        ZonedDateTime nowZdt = ZonedDateTime.now(InsightAlertEvaluator.resolveZone(properties.getQuietHoursZone()));
        if (InsightAlertEvaluator.inQuietHours(properties, nowZdt)) {
            log.debug("[告警] 静默时段内跳过推送");
            metrics.record("quiet");
            return;
        }

        int windowMinutes = properties.getWindowMinutes() > 0 ? properties.getWindowMinutes() : 15;
        long since = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES).toEpochMilli();
        long now = System.currentTimeMillis();
        String at = Instant.ofEpochMilli(now).truncatedTo(ChronoUnit.SECONDS).toString();

        if (InsightAlertEvaluator.isLatencyMetric(properties)) {
            scanLatency(properties, since, now, at);
        } else {
            scanErrors(properties, since, now, at);
        }
    }

    private void scanErrors(InsightServerAlertProperties properties, long since, long now, String at) {
        List<Map<String, Object>> rows = persistenceService.findHighErrorServicesSince(since);
        for (Map<String, Object> row : rows) {
            String service = String.valueOf(row.get("service_name"));
            long errorCalls = ((Number) row.get("error_calls")).longValue();
            double errorRate = ((Number) row.get("error_rate")).doubleValue();
            if (!InsightAlertEvaluator.exceeds(properties, errorCalls, errorRate)) {
                continue;
            }
            double value = InsightAlertEvaluator.metricValue(properties, errorCalls, errorRate);
            tryDeliver(properties, service, value, now, at);
        }
    }

    private void scanLatency(InsightServerAlertProperties properties, long since, long now, String at) {
        List<Map<String, Object>> rows = persistenceService.getServiceLatencySummariesSince(since, 50);
        for (Map<String, Object> row : rows) {
            String service = String.valueOf(row.get("service_name"));
            long p95 = ((Number) row.get("p95_ms")).longValue();
            if (!InsightAlertEvaluator.exceedsLatency(properties, p95)) {
                continue;
            }
            tryDeliver(properties, service, p95, now, at);
        }
    }

    private void tryDeliver(InsightServerAlertProperties properties,
                            String service,
                            double value,
                            long now,
                            String at) {
        Long fired = lastFiredAt.get(service);
        if (InsightAlertEvaluator.inCooldown(fired == null ? 0L : fired, properties.getCooldownMinutes(), now)) {
            metrics.record("cooldown");
            log.debug("[告警] 冷却中跳过: service={}", service);
            return;
        }
        Map<String, Object> payload = InsightAlertEvaluator.payload(properties, service, value, at);
        attachAiIfNeeded(payload, properties, service, value);
        boolean delivered = deliver(properties, payload);
        if (delivered) {
            lastFiredAt.put(service, now);
            metrics.record("success");
            log.info("[告警] 已推送: service={}, metric={}, value={}, aiAttached={}",
                    service, properties.normalizedMetric(), value, payload.getOrDefault("aiAttached", false));
        } else {
            metrics.record("failure");
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
     * @param properties 当前生效告警配置
     * @param payload    告警体
     * @return 任一已配置通道成功即为 true
     */
    private boolean deliver(InsightServerAlertProperties properties, Map<String, Object> payload) {
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
        return attempted && anySuccess;
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
