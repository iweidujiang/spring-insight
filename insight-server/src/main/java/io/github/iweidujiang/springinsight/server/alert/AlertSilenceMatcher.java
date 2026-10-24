package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 告警静默匹配：细粒度规则 + 兼容全局静默时段。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
public final class AlertSilenceMatcher {

    private AlertSilenceMatcher() {
    }

    /**
     * 当前告警是否应静默（跳过推送）。
     *
     * @param rules      细粒度规则；可空
     * @param properties 告警配置（含全局 quiet hours）
     * @param service    服务名
     * @param metrics    本轮命中的指标（可多个）
     * @param now        当前时刻
     * @return true 表示静默
     */
    public static boolean isSilenced(List<SilenceRule> rules,
                                     InsightServerAlertProperties properties,
                                     String service,
                                     Collection<String> metrics,
                                     ZonedDateTime now) {
        if (now == null) {
            return false;
        }
        List<SilenceRule> all = new ArrayList<>();
        if (rules != null) {
            all.addAll(rules);
        }
        SilenceRule global = fromQuietHours(properties);
        if (global != null) {
            all.add(global);
        }
        for (SilenceRule rule : all) {
            if (matches(rule, service, metrics, now)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 单条规则是否匹配。
     *
     * @param rule    规则
     * @param service 服务
     * @param metrics 命中指标
     * @param now     当前时刻
     * @return true 匹配
     */
    public static boolean matches(SilenceRule rule,
                                  String service,
                                  Collection<String> metrics,
                                  ZonedDateTime now) {
        if (rule == null || now == null) {
            return false;
        }
        String svcRule = trim(rule.getServiceName());
        String svc = service == null ? "" : service.trim();
        if (StringUtils.hasText(svcRule) && !svcRule.equalsIgnoreCase(svc)) {
            return false;
        }

        String metricRule = trim(rule.getMetric()).toLowerCase();
        if (StringUtils.hasText(metricRule)) {
            boolean metricHit = false;
            if (metrics != null) {
                for (String m : metrics) {
                    if (metricRule.equalsIgnoreCase(trim(m))) {
                        metricHit = true;
                        break;
                    }
                }
            }
            if (!metricHit) {
                return false;
            }
        }

        return inWindow(rule.getStartTime(), rule.getEndTime(), rule.getTimezone(), now);
    }

    /**
     * 将全局静默时段收成一条「服务空、指标空」的规则；未启用时返回 null。
     *
     * @param properties 告警配置
     * @return 合成规则或 null
     */
    public static SilenceRule fromQuietHours(InsightServerAlertProperties properties) {
        if (properties == null || !properties.isQuietHoursEnabled()) {
            return null;
        }
        SilenceRule rule = new SilenceRule();
        rule.setId("__global_quiet_hours__");
        rule.setServiceName("");
        rule.setMetric("");
        rule.setStartTime(properties.getQuietHoursStart());
        rule.setEndTime(properties.getQuietHoursEnd());
        rule.setTimezone(properties.getQuietHoursZone());
        rule.setReason("全局静默时段");
        return rule;
    }

    /**
     * 当前本地时间是否落在 [start, end)（含跨午夜；起止相同=全天）。
     *
     * @param startTime HH:mm
     * @param endTime   HH:mm
     * @param timezone  时区
     * @param now       当前
     * @return true 在窗内
     */
    public static boolean inWindow(String startTime, String endTime, String timezone, ZonedDateTime now) {
        LocalTime start = InsightAlertEvaluator.parseHhMm(startTime);
        LocalTime end = InsightAlertEvaluator.parseHhMm(endTime);
        if (start == null || end == null) {
            return false;
        }
        ZoneId zone = InsightAlertEvaluator.resolveZone(timezone);
        LocalTime local = now.withZoneSameInstant(zone).toLocalTime();
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !local.isBefore(start) && local.isBefore(end);
        }
        return !local.isBefore(start) || local.isBefore(end);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
