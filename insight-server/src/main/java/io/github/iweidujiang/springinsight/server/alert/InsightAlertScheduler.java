package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettingsService;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时扫描服务错误窗口，超阈值则推送 Webhook 和/或邮件。
 * <p>
 * 是否启用以 {@link InsightRuntimeSettingsService#effectiveAlert()} 为准（控制台可改，无需重启）。
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
    private final InsightAlertWebhookSender webhookSender;
    private final InsightAlertEmailSender emailSender;
    private final InsightAlertMetrics metrics;

    /** 服务名 → 上次成功推送的 epoch 毫秒 */
    private final ConcurrentHashMap<String, Long> lastFiredAt = new ConcurrentHashMap<>();
    private final AtomicBoolean warnedNotReady = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler;

    /**
     * @param settingsService     运行时设置
     * @param persistenceService  错误聚合
     * @param webhookSender       Webhook
     * @param emailSender         邮件
     * @param metrics             结果计数
     */
    public InsightAlertScheduler(InsightRuntimeSettingsService settingsService,
                                 TraceSpanPersistenceService persistenceService,
                                 InsightAlertWebhookSender webhookSender,
                                 InsightAlertEmailSender emailSender,
                                 InsightAlertMetrics metrics) {
        this.settingsService = settingsService;
        this.persistenceService = persistenceService;
        this.webhookSender = webhookSender;
        this.emailSender = emailSender;
        this.metrics = metrics;
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
     * 按窗口聚合错误，对超阈值且不在冷却期的服务推送已配置通道。
     */
    void scan() {
        InsightServerAlertProperties properties = settingsService.effectiveAlert();
        if (!properties.isEnabled()) {
            return;
        }
        if (!properties.isSendReady()) {
            if (warnedNotReady.compareAndSet(false, true)) {
                log.warn("[告警] 已启用但未配置可用通道（webhook-url 或 email.host/from/to）");
            }
            return;
        }
        warnedNotReady.set(false);
        if (!InsightAlertEvaluator.supportedMetric(properties)) {
            log.warn("[告警] 不支持的 metric={}，仅接受 error_rate / error_count", properties.normalizedMetric());
            return;
        }

        int windowMinutes = properties.getWindowMinutes() > 0 ? properties.getWindowMinutes() : 15;
        long since = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES).toEpochMilli();
        List<Map<String, Object>> rows = persistenceService.findHighErrorServicesSince(since);
        long now = System.currentTimeMillis();
        String at = Instant.ofEpochMilli(now).truncatedTo(ChronoUnit.SECONDS).toString();

        for (Map<String, Object> row : rows) {
            String service = String.valueOf(row.get("service_name"));
            long errorCalls = ((Number) row.get("error_calls")).longValue();
            double errorRate = ((Number) row.get("error_rate")).doubleValue();
            if (!InsightAlertEvaluator.exceeds(properties, errorCalls, errorRate)) {
                continue;
            }
            Long fired = lastFiredAt.get(service);
            if (InsightAlertEvaluator.inCooldown(fired == null ? 0L : fired, properties.getCooldownMinutes(), now)) {
                metrics.record("cooldown");
                log.debug("[告警] 冷却中跳过: service={}", service);
                continue;
            }
            double value = InsightAlertEvaluator.metricValue(properties, errorCalls, errorRate);
            Map<String, Object> payload = InsightAlertEvaluator.payload(properties, service, value, at);
            boolean delivered = deliver(properties, payload);
            if (delivered) {
                lastFiredAt.put(service, now);
                metrics.record("success");
                log.info("[告警] 已推送: service={}, metric={}, value={}", service, properties.normalizedMetric(), value);
            } else {
                metrics.record("failure");
            }
        }
    }

    /**
     * @param properties 当前生效告警配置
     * @param payload    告警体
     * @return 任一已配置通道成功即为 true
     */
    private boolean deliver(InsightServerAlertProperties properties, Map<String, Object> payload) {
        boolean anySuccess = false;
        boolean attempted = false;
        if (properties.isWebhookSendReady()) {
            attempted = true;
            boolean ok = webhookSender.post(properties.normalizedWebhookUrl(), payload);
            metrics.record("webhook", ok ? "success" : "failure");
            anySuccess = anySuccess || ok;
        }
        if (properties.isEmailSendReady()) {
            attempted = true;
            boolean ok = emailSender.send(properties, payload);
            metrics.record("email", ok ? "success" : "failure");
            anySuccess = anySuccess || ok;
        }
        return attempted && anySuccess;
    }
}
