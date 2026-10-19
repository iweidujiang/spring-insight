package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 告警阈值、静默时段与冷却判定。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class InsightAlertEvaluatorTest {

    /**
     * error_rate 用百分比比较；error_count 用次数。
     */
    @Test
    void exceedsByMetric() {
        InsightServerAlertProperties rate = props("error_rate", 10.0);
        assertTrue(InsightAlertEvaluator.exceeds(rate, 1, 50.0));
        assertFalse(InsightAlertEvaluator.exceeds(rate, 1, 9.9));

        InsightServerAlertProperties count = props("error_count", 3.0);
        assertTrue(InsightAlertEvaluator.exceeds(count, 3, 1.0));
        assertFalse(InsightAlertEvaluator.exceeds(count, 2, 100.0));
        assertEquals(3.0, InsightAlertEvaluator.metricValue(count, 3, 80.0));
    }

    /**
     * latency_p95 用毫秒比较；错误类 exceeds 对其返回 false。
     */
    @Test
    void exceedsLatencyP95() {
        InsightServerAlertProperties p = props("latency_p95", 200.0);
        assertTrue(InsightAlertEvaluator.supportedMetric(p));
        assertTrue(InsightAlertEvaluator.isLatencyMetric(p));
        assertTrue(InsightAlertEvaluator.exceedsLatency(p, 200L));
        assertFalse(InsightAlertEvaluator.exceedsLatency(p, 199L));
        assertFalse(InsightAlertEvaluator.exceeds(p, 99, 99.0));
        assertEquals("latency_threshold", InsightAlertEvaluator.payloadType(p));
    }

    /**
     * 旧的单个指标和阈值按大于等于收成一条条件。
     */
    @Test
    void legacySingleConditionStillFires() {
        InsightServerAlertProperties rate = props("error_rate", 10.0);
        AlertEvaluation hit = InsightAlertEvaluator.evaluate(rate, 50.0, 1, 0, 2);
        assertTrue(hit.fired());
        assertEquals(1, hit.matched().size());
        assertEquals("error_rate", hit.matched().get(0).condition().getMetric());
        assertEquals("gte", hit.matched().get(0).condition().getOperator());
        assertEquals(50.0, hit.matched().get(0).value());

        AlertEvaluation miss = InsightAlertEvaluator.evaluate(rate, 9.9, 1, 0, 2);
        assertFalse(miss.fired());
        assertTrue(miss.matched().isEmpty());
    }

    /**
     * 多条条件全部成立才触发，结果里带上每一条。
     */
    @Test
    void allConditionsMustMatch() {
        InsightServerAlertProperties rules = props("error_rate", 1.0);
        rules.setConditions(List.of(
                condition("error_rate", "gte", 10.0),
                condition("latency_p95", "gt", 200.0),
                condition("span_count", "eq", 4.0)));

        AlertEvaluation hit = InsightAlertEvaluator.evaluate(rules, 12.0, 1, 250, 4);
        assertTrue(hit.fired());
        assertEquals(3, hit.matched().size());
        assertEquals("latency_p95", hit.matched().get(1).condition().getMetric());
        assertEquals("error_rate", rules.effectiveConditions().get(0).getMetric());
    }

    /**
     * 只满足其中一部分时不触发，但仍返回已经成立的条件。
     */
    @Test
    void partialConditionsDoNotFire() {
        InsightServerAlertProperties rules = props("error_count", 99.0);
        rules.setConditions(List.of(
                condition("error_rate", "gte", 10.0),
                condition("latency_p95", "lte", 100.0)));

        AlertEvaluation partial = InsightAlertEvaluator.evaluate(rules, 50.0, 9, 180, 3);
        assertFalse(partial.fired());
        assertEquals(1, partial.matched().size());
        assertEquals("error_rate", partial.matched().get(0).condition().getMetric());
        assertEquals("error_rate", rules.effectiveConditions().get(0).getMetric());
    }

    /**
     * 未知指标不触发。
     */
    @Test
    void unknownMetricDoesNotExceed() {
        InsightServerAlertProperties p = props("latency", 1.0);
        assertFalse(InsightAlertEvaluator.supportedMetric(p));
        assertFalse(InsightAlertEvaluator.exceeds(p, 99, 99.0));
    }

    /**
     * 冷却窗口内跳过；窗口外或 cooldown=0 不跳过。
     */
    @Test
    void cooldownWindow() {
        long now = 1_000_000L;
        assertTrue(InsightAlertEvaluator.inCooldown(now - 60_000L, 30, now));
        assertFalse(InsightAlertEvaluator.inCooldown(now - 31 * 60_000L, 30, now));
        assertFalse(InsightAlertEvaluator.inCooldown(0L, 30, now));
        assertFalse(InsightAlertEvaluator.inCooldown(now, 0, now));
    }

    /**
     * 同日静默窗与跨午夜静默窗。
     */
    @Test
    void quietHoursSameDayAndOvernight() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate day = LocalDate.of(2026, 10, 11);

        InsightServerAlertProperties sameDay = props("error_rate", 10.0);
        sameDay.setQuietHoursEnabled(true);
        sameDay.setQuietHoursStart("09:00");
        sameDay.setQuietHoursEnd("17:00");
        sameDay.setQuietHoursZone("Asia/Shanghai");
        assertTrue(InsightAlertEvaluator.inQuietHours(sameDay, at(day, "10:00", zone)));
        assertFalse(InsightAlertEvaluator.inQuietHours(sameDay, at(day, "08:59", zone)));
        assertFalse(InsightAlertEvaluator.inQuietHours(sameDay, at(day, "17:00", zone)));

        InsightServerAlertProperties overnight = props("error_rate", 10.0);
        overnight.setQuietHoursEnabled(true);
        overnight.setQuietHoursStart("22:00");
        overnight.setQuietHoursEnd("08:00");
        overnight.setQuietHoursZone("Asia/Shanghai");
        assertTrue(InsightAlertEvaluator.inQuietHours(overnight, at(day, "23:00", zone)));
        assertTrue(InsightAlertEvaluator.inQuietHours(overnight, at(day, "07:59", zone)));
        assertFalse(InsightAlertEvaluator.inQuietHours(overnight, at(day, "08:00", zone)));
        assertFalse(InsightAlertEvaluator.inQuietHours(overnight, at(day, "12:00", zone)));

        overnight.setQuietHoursEnabled(false);
        assertFalse(InsightAlertEvaluator.inQuietHours(overnight, at(day, "23:00", zone)));
    }

    /**
     * payload 字段名与 M0 冻结草案一致；预览带 preview=true。
     */
    @Test
    void payloadKeys() {
        InsightServerAlertProperties p = props("error_rate", 10.0);
        p.setWindowMinutes(15);
        Map<String, Object> body = InsightAlertEvaluator.payload(p, "sca-order", 42.5, "2026-09-18T02:00:00Z");
        assertEquals("1", body.get("schemaVersion"));
        assertEquals("error_threshold", body.get("type"));
        assertEquals("error_rate", body.get("metric"));
        assertEquals(10.0, body.get("threshold"));
        assertEquals(15, body.get("windowMinutes"));
        assertEquals("sca-order", body.get("serviceName"));
        assertEquals(42.5, body.get("value"));
        assertEquals("2026-09-18T02:00:00Z", body.get("at"));

        Map<String, Object> preview = InsightAlertEvaluator.previewPayload(p, "2026-09-18T02:00:00Z");
        assertEquals(Boolean.TRUE, preview.get("preview"));
        assertEquals("preview-service", preview.get("serviceName"));
    }

    private static ZonedDateTime at(LocalDate day, String hhmm, ZoneId zone) {
        return ZonedDateTime.of(day, LocalTime.parse(hhmm), zone);
    }

    private static AlertCondition condition(String metric, String operator, double threshold) {
        AlertCondition condition = new AlertCondition();
        condition.setMetric(metric);
        condition.setOperator(operator);
        condition.setThreshold(threshold);
        return condition;
    }

    private static InsightServerAlertProperties props(String metric, double threshold) {
        InsightServerAlertProperties p = new InsightServerAlertProperties();
        p.setEnabled(true);
        p.setWebhookUrl("http://127.0.0.1/hook");
        p.setMetric(metric);
        p.setThreshold(threshold);
        p.setWindowMinutes(15);
        p.setCooldownMinutes(30);
        return p;
    }
}
