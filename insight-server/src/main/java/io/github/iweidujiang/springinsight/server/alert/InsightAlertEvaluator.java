package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警阈值、静默时段与冷却判定（无 IO）。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class InsightAlertEvaluator {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("H:mm");

    private InsightAlertEvaluator() {
    }

    /**
     * @param properties 告警配置
     * @return 是否为已支持的 error_rate / error_count / latency_p95
     */
    public static boolean supportedMetric(InsightServerAlertProperties properties) {
        String metric = properties.normalizedMetric();
        return "error_rate".equals(metric)
                || "error_count".equals(metric)
                || "latency_p95".equals(metric);
    }

    /**
     * @param properties 告警配置
     * @return 是否为错误类指标
     */
    public static boolean isErrorMetric(InsightServerAlertProperties properties) {
        String metric = properties.normalizedMetric();
        return "error_rate".equals(metric) || "error_count".equals(metric);
    }

    /**
     * @param properties 告警配置
     * @return 是否为 p95 延迟指标
     */
    public static boolean isLatencyMetric(InsightServerAlertProperties properties) {
        return "latency_p95".equals(properties.normalizedMetric());
    }

    /**
     * @param properties 告警配置
     * @param errorCalls 窗口内错误 Span 数
     * @param errorRate  错误率（百分比）
     * @return 是否达到或超过阈值（仅错误类指标）
     */
    public static boolean exceeds(InsightServerAlertProperties properties, long errorCalls, double errorRate) {
        if (!isErrorMetric(properties)) {
            return false;
        }
        return exceedsValue(properties, metricValue(properties, errorCalls, errorRate));
    }

    /**
     * @param properties 告警配置
     * @param p95Ms      窗口内 p95 耗时（毫秒）
     * @return 是否达到或超过阈值（仅 latency_p95）
     */
    public static boolean exceedsLatency(InsightServerAlertProperties properties, long p95Ms) {
        if (!isLatencyMetric(properties)) {
            return false;
        }
        return exceedsValue(properties, p95Ms);
    }

    /**
     * @param properties 告警配置
     * @param value      当前指标值
     * @return value &gt;= threshold
     */
    public static boolean exceedsValue(InsightServerAlertProperties properties, double value) {
        if (!supportedMetric(properties)) {
            return false;
        }
        return value >= properties.getThreshold();
    }

    /**
     * @param properties 告警配置
     * @param errorCalls 窗口内错误 Span 数
     * @param errorRate  错误率（百分比）
     * @return 写入 payload 的 value（错误类）
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
     * 当前时刻是否落在静默时段内（含跨午夜）。未启用或解析失败时视为非静默。
     *
     * @param properties 告警配置
     * @param now        当前时刻（带时区）
     * @return true 表示应跳过推送
     */
    public static boolean inQuietHours(InsightServerAlertProperties properties, ZonedDateTime now) {
        if (properties == null || !properties.isQuietHoursEnabled()) {
            return false;
        }
        LocalTime start = parseHhMm(properties.getQuietHoursStart());
        LocalTime end = parseHhMm(properties.getQuietHoursEnd());
        if (start == null || end == null) {
            return false;
        }
        ZoneId zone = resolveZone(properties.getQuietHoursZone());
        LocalTime local = now.withZoneSameInstant(zone).toLocalTime();
        if (start.equals(end)) {
            // 起止相同视为全天静默
            return true;
        }
        if (start.isBefore(end)) {
            // 同日窗： [start, end)
            return !local.isBefore(start) && local.isBefore(end);
        }
        // 跨午夜： [start, 24:00) ∪ [00:00, end)
        return !local.isBefore(start) || local.isBefore(end);
    }

    /**
     * @param zoneId 配置时区；空则 JVM 默认
     * @return ZoneId
     */
    public static ZoneId resolveZone(String zoneId) {
        if (!StringUtils.hasText(zoneId)) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(zoneId.trim());
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    /**
     * @param hhmm {@code H:mm} / {@code HH:mm}
     * @return 解析结果；失败为 null
     */
    public static LocalTime parseHhMm(String hhmm) {
        if (!StringUtils.hasText(hhmm)) {
            return null;
        }
        try {
            return LocalTime.parse(hhmm.trim(), HH_MM);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * @param properties 告警配置
     * @return payload.type：错误类为 error_threshold，延迟为 latency_threshold
     */
    public static String payloadType(InsightServerAlertProperties properties) {
        return isLatencyMetric(properties) ? "latency_threshold" : "error_threshold";
    }

    /**
     * @param properties  告警配置
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
        body.put("type", payloadType(properties));
        body.put("metric", properties.normalizedMetric());
        body.put("threshold", properties.getThreshold());
        body.put("windowMinutes", properties.getWindowMinutes());
        body.put("serviceName", serviceName);
        body.put("value", value);
        body.put("at", atIso);
        return body;
    }

    /**
     * 构造预览用样本 payload（不依赖实时扫描）。
     *
     * @param properties 告警配置
     * @param atIso      时间戳
     * @return 样本体，含 {@code preview=true}
     */
    public static Map<String, Object> previewPayload(InsightServerAlertProperties properties, String atIso) {
        double sampleValue;
        if (isLatencyMetric(properties)) {
            sampleValue = Math.max(properties.getThreshold(), 1.0);
        } else if ("error_count".equals(properties.normalizedMetric())) {
            sampleValue = Math.max(properties.getThreshold(), 1.0);
        } else {
            sampleValue = Math.max(properties.getThreshold(), 1.0);
        }
        Map<String, Object> body = payload(properties, "preview-service", sampleValue, atIso);
        body.put("preview", true);
        return body;
    }
}
