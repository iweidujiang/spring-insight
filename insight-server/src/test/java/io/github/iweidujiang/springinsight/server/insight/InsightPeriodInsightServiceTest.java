package io.github.iweidujiang.springinsight.server.insight;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时段事实与环比。
 *
 * @since 2026-09-26
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class InsightPeriodInsightServiceTest {

    /**
     * hours&lt;=0 时不做环比。
     */
    @Test
    @SuppressWarnings("unchecked")
    void noCompareWhenHoursZero() {
        InsightPeriodInsightService svc = serviceWith(
                span("a", "t1", false, hoursAgo(1)));
        Map<String, Object> body = svc.build(0);
        assertFalse(Boolean.TRUE.equals(body.get("compare")));
        assertNull(body.get("delta"));
        Map<String, Object> current = (Map<String, Object>) body.get("current");
        assertEquals(1L, ((Number) current.get("spanCount")).longValue());
        assertNotNull(body.get("headline"));
    }

    /**
     * 当前窗错误多于上一窗时 delta.errorSpanCount &gt; 0。
     */
    @Test
    @SuppressWarnings("unchecked")
    void deltaErrorIncreases() {
        long now = System.currentTimeMillis();
        // 当前窗（近 1h）：2 条错误
        TraceSpan cur1 = span("order", "t-cur-1", false, now - 10 * 60_000L);
        TraceSpan cur2 = span("order", "t-cur-2", false, now - 20 * 60_000L);
        // 上一窗（1h～2h 前）：1 条错误
        TraceSpan prev = span("order", "t-prev", false, now - 90 * 60_000L);
        // 更早：不计
        TraceSpan old = span("order", "t-old", false, now - 5 * 3_600_000L);

        InsightPeriodInsightService svc = serviceWith(cur1, cur2, prev, old);
        Map<String, Object> body = svc.build(1);
        assertTrue(Boolean.TRUE.equals(body.get("compare")));
        Map<String, Object> current = (Map<String, Object>) body.get("current");
        Map<String, Object> previous = (Map<String, Object>) body.get("previous");
        assertEquals(2L, ((Number) current.get("errorSpanCount")).longValue());
        assertEquals(1L, ((Number) previous.get("errorSpanCount")).longValue());
        Map<String, Object> delta = (Map<String, Object>) body.get("delta");
        assertEquals(1L, ((Number) delta.get("errorSpanCount")).longValue());
        assertTrue(String.valueOf(body.get("headline")).contains("错误"));
        Map<String, Object> links = (Map<String, Object>) body.get("links");
        assertNotNull(links.get("errorAnalysis"));
        assertNotNull(links.get("errorTraces"));
    }

    /**
     * summarizeWindow Top 按错误数排序。
     */
    @Test
    @SuppressWarnings("unchecked")
    void summarizeWindowTopsErrors() {
        long now = System.currentTimeMillis();
        TraceSpanPersistenceService persistence = persistence(
                span("a", "t1", false, now - 1000),
                span("a", "t2", false, now - 2000),
                span("b", "t3", false, now - 3000),
                span("b", "t4", true, now - 4000)
        );
        Map<String, Object> snap = persistence.summarizeWindow(now - 10_000L, now + 1, 5);
        assertEquals(4L, ((Number) snap.get("spanCount")).longValue());
        assertEquals(3L, ((Number) snap.get("errorSpanCount")).longValue());
        List<Map<String, Object>> errSvc = (List<Map<String, Object>>) snap.get("errorServices");
        assertEquals("a", errSvc.get(0).get("serviceName"));
        assertEquals(2L, ((Number) errSvc.get(0).get("errorSpans")).longValue());
    }

    private static InsightPeriodInsightService serviceWith(TraceSpan... spans) {
        return new InsightPeriodInsightService(persistence(spans));
    }

    private static TraceSpanPersistenceService persistence(TraceSpan... spans) {
        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        InMemorySpanStore store = new InMemorySpanStore(storage, "memory");
        TraceSpanPersistenceService persistence = new TraceSpanPersistenceService(store);
        persistence.saveTraceSpans(List.of(spans));
        return persistence;
    }

    private static TraceSpan span(String service, String traceId, boolean ok, long startMs) {
        TraceSpan s = new TraceSpan();
        s.setServiceName(service);
        s.setTraceId(traceId);
        s.setSpanId(traceId + "-s");
        s.setSuccess(ok);
        s.setStatusCode(ok ? "OK" : "ERROR");
        s.setStartTime(startMs);
        s.setDurationMs(ok ? 10L : 50L);
        s.setOperationName("GET /x");
        return s;
    }

    private static long hoursAgo(int h) {
        return System.currentTimeMillis() - h * 3_600_000L;
    }
}
