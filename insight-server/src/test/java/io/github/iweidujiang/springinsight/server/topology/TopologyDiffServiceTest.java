package io.github.iweidujiang.springinsight.server.topology;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拓扑对比服务：相邻时段窗口上的差异。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
class TopologyDiffServiceTest {

    private TopologyDiffService service;
    private long now;

    /**
     * hoursB=1 近窗、hoursA=24 基线窗（紧邻近窗之前）各有不同边。
     */
    @BeforeEach
    void setUp() {
        now = 1_700_000_000_000L;
        long hour = 3_600_000L;
        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        InMemorySpanStore store = new InMemorySpanStore(storage, "memory");
        TraceSpanPersistenceService persistence = new TraceSpanPersistenceService(store);

        List<TraceSpan> spans = new ArrayList<>();
        // 基线窗 [now-25h, now-1h)：gateway→product、order→user×10
        addEdges(spans, "sca-gateway", "sca-product", now - 20 * hour, 3);
        addEdges(spans, "sca-order", "sca-user", now - 10 * hour, 10);
        // 近窗 [now-1h, now)：order→user×50、order→loyalty×4
        addEdges(spans, "sca-order", "sca-user", now - hour / 2, 50);
        addEdges(spans, "sca-order", "sca-loyalty", now - hour / 3, 4);

        persistence.saveTraceSpans(spans);
        service = new TopologyDiffService(persistence);
    }

    /**
     * 检出新增 / 消失 / 调用量显著变化。
     */
    @Test
    @SuppressWarnings("unchecked")
    void diff_returnsExpectedCategories() {
        Map<String, Object> body = service.diff(24, 1, now);

        List<Map<String, Object>> added = (List<Map<String, Object>>) body.get("added");
        List<Map<String, Object>> removed = (List<Map<String, Object>>) body.get("removed");
        List<Map<String, Object>> changed = (List<Map<String, Object>>) body.get("changed");

        assertTrue(added.stream().anyMatch(r ->
                "sca-order".equals(r.get("from")) && "sca-loyalty".equals(r.get("to"))
                        && ((Number) r.get("count")).longValue() == 4L));
        assertTrue(removed.stream().anyMatch(r ->
                "sca-gateway".equals(r.get("from")) && "sca-product".equals(r.get("to"))
                        && String.valueOf(r.getOrDefault("lastSeen", "")).length() > 0));
        Map<String, Object> ch = changed.stream()
                .filter(r -> "sca-order".equals(r.get("from")) && "sca-user".equals(r.get("to")))
                .findFirst()
                .orElseThrow();
        assertEquals(10L, ((Number) ch.get("countBefore")).longValue());
        assertEquals(50L, ((Number) ch.get("countAfter")).longValue());
        assertEquals(4.0, ((Number) ch.get("changeRate")).doubleValue(), 1e-6);
    }

    /**
     * 两窗数据对称且调用量相同时无差异。
     */
    @Test
    @SuppressWarnings("unchecked")
    void diff_identicalWindowsEmpty() {
        long hour = 3_600_000L;
        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        InMemorySpanStore store = new InMemorySpanStore(storage, "memory");
        TraceSpanPersistenceService persistence = new TraceSpanPersistenceService(store);
        List<TraceSpan> spans = new ArrayList<>();
        addEdges(spans, "a", "b", now - 3 * hour / 2, 5);
        addEdges(spans, "a", "b", now - hour / 2, 5);
        persistence.saveTraceSpans(spans);
        TopologyDiffService svc = new TopologyDiffService(persistence);

        Map<String, Object> body = svc.diff(1, 1, now);
        assertTrue(((List<?>) body.get("added")).isEmpty());
        assertTrue(((List<?>) body.get("removed")).isEmpty());
        assertTrue(((List<?>) body.get("changed")).isEmpty());
    }

    private static void addEdges(List<TraceSpan> spans, String from, String to, long startTime, int count) {
        for (int i = 0; i < count; i++) {
            TraceSpan s = new TraceSpan();
            s.setTraceId(from + "-" + to + "-" + startTime + "-" + i);
            s.setSpanId("s-" + startTime + "-" + i);
            s.setServiceName(from);
            s.setRemoteService(to);
            s.setOperationName("CALL");
            s.setStartTime(startTime + i);
            s.setDurationMs(5L);
            s.setSuccess(true);
            spans.add(s);
        }
    }
}
