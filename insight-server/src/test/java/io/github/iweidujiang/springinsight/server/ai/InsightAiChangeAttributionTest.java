package io.github.iweidujiang.springinsight.server.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 变更归因窗口与 diff 逻辑。
 *
 * @author 苏渡苇
 * @since 2026-10-23
 */
class InsightAiChangeAttributionTest {

    /**
     * before / after 半开窗口相邻且长度正确。
     */
    @Test
    void bounds_adjacentWindows() {
        long now = 1_700_000_000_000L;
        InsightAiChangeAttribution.WindowBounds b = InsightAiChangeAttribution.bounds(2, 2, now);
        assertEquals(2, b.hoursBefore());
        assertEquals(2, b.hoursAfter());
        assertEquals(now - 4L * 3_600_000L, b.beforeFrom());
        assertEquals(now - 2L * 3_600_000L, b.beforeTo());
        assertEquals(b.beforeTo(), b.afterFrom());
        assertEquals(now, b.afterTo());
    }

    /**
     * 错误率上升、新增错误类型与依赖边进入 changes。
     */
    @Test
    @SuppressWarnings("unchecked")
    void diffChanges_detectsErrorRateAndNewTypes() {
        Map<String, Object> before = Map.of(
                "services", List.of(Map.of(
                        "serviceName", "sca-user",
                        "requestCount", 100L,
                        "errorCount", 0L,
                        "errorRate", 0.0,
                        "p95Ms", 40L)),
                "errorTypes", List.of(Map.of(
                        "category", "status",
                        "key", "404",
                        "label", "HTTP 404",
                        "count", 1L)),
                "edges", List.of(Map.of(
                        "sourceService", "sca-user",
                        "targetService", "user-db",
                        "callCount", 10L)));
        Map<String, Object> after = Map.of(
                "services", List.of(Map.of(
                        "serviceName", "sca-user",
                        "requestCount", 80L,
                        "errorCount", 10L,
                        "errorRate", 12.5,
                        "p95Ms", 45L)),
                "errorTypes", List.of(
                        Map.of("category", "status", "key", "404", "label", "HTTP 404", "count", 2L),
                        Map.of("category", "exception", "key", "NullPointerException",
                                "label", "NullPointerException", "count", 8L)),
                "edges", List.of(
                        Map.of("sourceService", "sca-user", "targetService", "user-db", "callCount", 12L),
                        Map.of("sourceService", "sca-user", "targetService", "redis", "callCount", 5L)));

        List<Map<String, Object>> changes = InsightAiChangeAttribution.diffChanges(before, after);
        assertFalse(changes.isEmpty());

        Map<String, Object> errRate = changes.stream()
                .filter(c -> "error_rate".equals(c.get("metric")))
                .findFirst()
                .orElseThrow();
        assertEquals("sca-user", errRate.get("service"));
        assertEquals(0.0, ((Number) errRate.get("before")).doubleValue(), 0.01);
        assertEquals(12.5, ((Number) errRate.get("after")).doubleValue(), 0.01);
        assertEquals(12.5, ((Number) errRate.get("delta")).doubleValue(), 0.01);

        assertTrue(changes.stream().anyMatch(c -> "new_error_type".equals(c.get("metric"))
                && "NullPointerException".equals(c.get("key"))));
        assertTrue(changes.stream().anyMatch(c -> "new_dependency".equals(c.get("metric"))
                && "redis".equals(c.get("target"))));

        String summary = InsightAiChangeAttribution.factSummary(changes, 2, 2);
        assertTrue(summary.contains("sca-user"));
        assertTrue(summary.contains("12.5") || summary.contains("错误率"));
    }

    /**
     * 无显著差异时摘要说明未发现变化。
     */
    @Test
    void factSummary_emptyChanges() {
        String s = InsightAiChangeAttribution.factSummary(List.of(), 2, 2);
        assertTrue(s.contains("未发现"));
    }
}
