package io.github.iweidujiang.springinsight.server.topology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拓扑边 diff：新增 / 删除 / 调用量变化。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
class TopologyDiffTest {

    /**
     * 完全相同 → 三个列表皆空。
     */
    @Test
    @SuppressWarnings("unchecked")
    void diff_identicalReturnsEmptyLists() {
        List<Map<String, Object>> edges = List.of(
                Map.of("source_service", "sca-order", "target_service", "sca-user", "call_count", 10L));
        Map<String, Object> body = TopologyDiff.diff(edges, edges);
        assertTrue(((List<?>) body.get("added")).isEmpty());
        assertTrue(((List<?>) body.get("removed")).isEmpty());
        assertTrue(((List<?>) body.get("changed")).isEmpty());
    }

    /**
     * 识别 added / removed / changed（变化 &gt; 50%）。
     */
    @Test
    @SuppressWarnings("unchecked")
    void diff_detectsAddedRemovedChanged() {
        List<Map<String, Object>> a = List.of(
                Map.of("source_service", "sca-order", "target_service", "sca-user",
                        "call_count", 10L, "last_seen", "2026-10-20T10:00:00Z"),
                Map.of("source_service", "sca-gateway", "target_service", "sca-product",
                        "call_count", 3L, "last_seen", "2026-10-20T09:00:00Z"));
        List<Map<String, Object>> b = List.of(
                Map.of("source_service", "sca-order", "target_service", "sca-user", "call_count", 50L),
                Map.of("source_service", "sca-order", "target_service", "sca-loyalty", "call_count", 4L));

        Map<String, Object> body = TopologyDiff.diff(a, b);
        List<Map<String, Object>> added = (List<Map<String, Object>>) body.get("added");
        List<Map<String, Object>> removed = (List<Map<String, Object>>) body.get("removed");
        List<Map<String, Object>> changed = (List<Map<String, Object>>) body.get("changed");

        assertEquals(1, added.size());
        assertEquals("sca-order", added.get(0).get("from"));
        assertEquals("sca-loyalty", added.get(0).get("to"));
        assertEquals(4L, ((Number) added.get(0).get("count")).longValue());

        assertEquals(1, removed.size());
        assertEquals("sca-gateway", removed.get(0).get("from"));
        assertEquals("sca-product", removed.get(0).get("to"));
        assertEquals("2026-10-20T09:00:00Z", removed.get(0).get("lastSeen"));

        assertEquals(1, changed.size());
        assertEquals("sca-order", changed.get(0).get("from"));
        assertEquals("sca-user", changed.get(0).get("to"));
        assertEquals(10L, ((Number) changed.get(0).get("countBefore")).longValue());
        assertEquals(50L, ((Number) changed.get(0).get("countAfter")).longValue());
        assertEquals(4.0, ((Number) changed.get(0).get("changeRate")).doubleValue(), 1e-6);
    }

    /**
     * 变化不超过 50% 不进 changed。
     */
    @Test
    @SuppressWarnings("unchecked")
    void diff_smallChangeIgnored() {
        List<Map<String, Object>> a = List.of(
                Map.of("from", "a", "to", "b", "count", 100L));
        List<Map<String, Object>> b = List.of(
                Map.of("from", "a", "to", "b", "count", 140L));
        Map<String, Object> body = TopologyDiff.diff(a, b);
        assertTrue(((List<?>) body.get("changed")).isEmpty());
        assertTrue(((List<?>) body.get("added")).isEmpty());
        assertTrue(((List<?>) body.get("removed")).isEmpty());
    }

    /**
     * 相对变化刚好超过 50% 记入 changed。
     */
    @Test
    @SuppressWarnings("unchecked")
    void diff_overThresholdIsChanged() {
        List<Map<String, Object>> a = List.of(
                Map.of("from", "a", "to", "b", "count", 100L));
        List<Map<String, Object>> b = List.of(
                Map.of("from", "a", "to", "b", "count", 151L));
        List<Map<String, Object>> changed = (List<Map<String, Object>>) TopologyDiff.diff(a, b).get("changed");
        assertEquals(1, changed.size());
        assertEquals(0.51, ((Number) changed.get(0).get("changeRate")).doubleValue(), 1e-6);
    }
}
