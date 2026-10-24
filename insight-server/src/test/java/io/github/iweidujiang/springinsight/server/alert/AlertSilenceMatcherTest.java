package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 静默规则匹配：服务 / 指标通配与时段窗。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
class AlertSilenceMatcherTest {

    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private final LocalDate day = LocalDate.of(2026, 10, 24);

    /**
     * 空服务名 / 空指标 = 通配。
     */
    @Test
    void blankServiceAndMetricMatchAll() {
        SilenceRule rule = rule("", "", "00:00", "00:00");
        ZonedDateTime now = at("12:00");
        assertTrue(AlertSilenceMatcher.matches(rule, "any-svc", List.of("error_rate"), now));
        assertTrue(AlertSilenceMatcher.matches(rule, "other", List.of("latency_p95"), now));
    }

    /**
     * 按服务名过滤；大小写不敏感。
     */
    @Test
    void serviceFilter() {
        SilenceRule rule = rule("sca-order", "", "00:00", "00:00");
        ZonedDateTime now = at("12:00");
        assertTrue(AlertSilenceMatcher.matches(rule, "SCA-ORDER", List.of("error_rate"), now));
        assertFalse(AlertSilenceMatcher.matches(rule, "sca-user", List.of("error_rate"), now));
    }

    /**
     * 按指标过滤；命中列表中任一指标即可。
     */
    @Test
    void metricFilter() {
        SilenceRule rule = rule("", "error_rate", "00:00", "00:00");
        ZonedDateTime now = at("12:00");
        assertTrue(AlertSilenceMatcher.matches(rule, "svc", List.of("error_rate"), now));
        assertTrue(AlertSilenceMatcher.matches(rule, "svc", List.of("latency_p95", "error_rate"), now));
        assertFalse(AlertSilenceMatcher.matches(rule, "svc", List.of("latency_p95"), now));
        assertFalse(AlertSilenceMatcher.matches(rule, "svc", List.of(), now));
    }

    /**
     * 同日时段窗 [start, end)。
     */
    @Test
    void sameDayWindow() {
        SilenceRule rule = rule("", "", "09:00", "18:00");
        assertTrue(AlertSilenceMatcher.matches(rule, "s", List.of("m"), at("09:00")));
        assertTrue(AlertSilenceMatcher.matches(rule, "s", List.of("m"), at("17:59")));
        assertFalse(AlertSilenceMatcher.matches(rule, "s", List.of("m"), at("08:59")));
        assertFalse(AlertSilenceMatcher.matches(rule, "s", List.of("m"), at("18:00")));
    }

    /**
     * 全局 quiet hours 合成规则参与 isSilenced。
     */
    @Test
    void globalQuietHoursAsSyntheticRule() {
        InsightServerAlertProperties props = new InsightServerAlertProperties();
        props.setQuietHoursEnabled(true);
        props.setQuietHoursStart("00:00");
        props.setQuietHoursEnd("00:00");
        props.setQuietHoursZone("Asia/Shanghai");
        assertTrue(AlertSilenceMatcher.isSilenced(
                List.of(), props, "svc", List.of("error_rate"), at("15:00")));

        props.setQuietHoursEnabled(false);
        SilenceRule onlyOrder = rule("sca-order", "", "00:00", "00:00");
        assertTrue(AlertSilenceMatcher.isSilenced(
                List.of(onlyOrder), props, "sca-order", List.of("error_rate"), at("15:00")));
        assertFalse(AlertSilenceMatcher.isSilenced(
                List.of(onlyOrder), props, "sca-user", List.of("error_rate"), at("15:00")));
    }

    private SilenceRule rule(String service, String metric, String start, String end) {
        SilenceRule r = new SilenceRule();
        r.setId("t");
        r.setServiceName(service);
        r.setMetric(metric);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setTimezone("Asia/Shanghai");
        return r;
    }

    private ZonedDateTime at(String hhmm) {
        return ZonedDateTime.of(day, LocalTime.parse(hhmm), zone);
    }
}
