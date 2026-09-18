package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警阈值与冷却判定（无 IO）。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class InsightAlertEvaluator {

    private InsightAlertEvaluator() {
    }

    /**
     * @param properties 告警配置
     * @return 指标是否为已支持的 {@code error_rate} / {@code error_count}
     */
    public static boolean supportedMetric(InsightServerAlertProperties properties) {
        String metric = properties.normalizedMetric();
        return "error_rate".equals(metric) || "error_count".equals(metric);
    }

    /**
     * @param properties 告警配置
     * @param errorCalls 窗口内错误 Span 数
     * @param errorRate  错误率（百分比）
     * @return 是否达到或超过阈值
     */
    public static boolean exceeds(InsightServerAlertProperties properties, long errorCalls, double errorRate) {
        if (!supportedMetric(properties)) {
            return false;
        }
        return metricValue(properties, errorCalls, errorRate) >= properties.getThreshold();
    }

    /**
     * @param properties 告警配置
     * @param errorCalls 窗口内错误 Span 数
     * @param errorRate  错误率（百分比）
     * @return 写入 payload 的 value
     */
    public static double metricValue(InsightServerAlertProperties properties, long errorCalls, double errorRate) {
        if ("error_count".equals(properties.normalizedMetric())) {
            return errorCalls;
        }
        return errorRate;
    }

    /**
     * @param lastFiredAtMs   上次成功推送时间；{@code <=0} 表示从未推送
     * @param cooldownMinutes 冷却分钟
     * @param nowMs           当前时间
     * @return 是否仍在冷却期内（冷却 {@code <=0} 时永不冷却）
     */
    public static boolean inCooldown(long lastFiredAtMs, int cooldownMinutes, long nowMs) {
        if (lastFiredAtMs <= 0L || cooldownMinutes <= 0) {
            return false;
        }
        return nowMs - lastFiredAtMs < cooldownMinutes * 60_000L;
    }

    /**
     * @param properties 告警配置
     * @param serviceName 服务名
     * @param value       当前指标值
     * @param atIso       UTC 时间（ISO-8601）
     * @return Webhook JSON 字段（键名已冻结）
     */
    public static Map<String, Object> payload(InsightServerAlertProperties properties,
                                               String serviceName,
                                               double value,
                                               String atIso) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", "1");
        body.put("type", "error_threshold");
        body.put("metric", properties.normalizedMetric());
        body.put("threshold", properties.getThreshold());
        body.put("windowMinutes", properties.getWindowMinutes());
        body.put("serviceName", serviceName);
        body.put("value", value);
        body.put("at", atIso);
        return body;
    }
}
