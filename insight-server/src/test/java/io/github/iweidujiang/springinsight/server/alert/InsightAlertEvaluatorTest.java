package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 告警阈值与冷却判定。
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
     * payload 字段名与 M0 冻结草案一致。
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
