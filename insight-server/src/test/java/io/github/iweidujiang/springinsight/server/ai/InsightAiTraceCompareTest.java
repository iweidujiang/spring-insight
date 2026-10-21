package io.github.iweidujiang.springinsight.server.ai;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trace 对比对齐与显著差异筛选。
 *
 * @author 苏渡苇
 * @since 2026-10-22
 */
class InsightAiTraceCompareTest {

    @Test
    void alignsByOperationAndRemote_filtersOver20Percent() {
        TraceSpan a1 = span("tA", "s1", null, "GET /order", "order-db", 100);
        TraceSpan a2 = span("tA", "s2", "s1", "SELECT orders", "order-db", 80);
        TraceSpan b1 = span("tB", "s1", null, "GET /order", "order-db", 300);
        TraceSpan b2 = span("tB", "s2", "s1", "SELECT orders", "order-db", 85);

        List<InsightAiTraceCompare.DiffRow> diffs =
                InsightAiTraceCompare.significantDiffs(List.of(a1, a2), List.of(b1, b2));

        assertEquals(1, diffs.size());
        assertEquals("GET /order", diffs.get(0).span());
        assertEquals("order-db", diffs.get(0).service());
        assertEquals(100L, diffs.get(0).durationA());
        assertEquals(300L, diffs.get(0).durationB());
        assertEquals(200L, diffs.get(0).delta());
        assertTrue(diffs.get(0).ratio() >= 0.20);
    }

    @Test
    void relativeDiff_usesDurationAAsBase() {
        assertEquals(0.5, InsightAiTraceCompare.relativeDiff(100, 150), 0.0001);
        assertEquals(2.0, InsightAiTraceCompare.relativeDiff(100, 300), 0.0001);
        assertEquals(5.0, InsightAiTraceCompare.relativeDiff(0, 5), 0.0001);
    }

    @Test
    void rootDuration_prefersRootSpan() {
        TraceSpan root = span("t", "r", null, "HTTP GET", "", 500);
        TraceSpan child = span("t", "c", "r", "SELECT", "db", 400);
        assertEquals(500L, InsightAiTraceCompare.rootDurationMs(List.of(root, child)));
    }

    private static TraceSpan span(String traceId,
                                  String spanId,
                                  String parentId,
                                  String op,
                                  String remote,
                                  long durationMs) {
        TraceSpan s = new TraceSpan();
        s.setTraceId(traceId);
        s.setSpanId(spanId);
        s.setParentSpanId(parentId);
        s.setOperationName(op);
        s.setRemoteService(remote);
        s.setServiceName("svc");
        s.setDurationMs(durationMs);
        s.setStartTime(System.currentTimeMillis());
        return s;
    }
}
