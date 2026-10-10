package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trace 列表筛选：路径前缀与组合条件。
 *
 * @since 2026-10-08
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class TraceListFilterTest {

    @Test
    void normalizePathPrefixStripsMethodAndAddsSlash() {
        assertEquals("/api/orders", TraceSpanPersistenceService.normalizePathPrefix("GET /api/orders"));
        assertEquals("/api/orders", TraceSpanPersistenceService.normalizePathPrefix("api/orders"));
        assertEquals("", TraceSpanPersistenceService.normalizePathPrefix("  "));
    }

    @Test
    void operationPathMatchesPrefix() {
        assertTrue(TraceSpanPersistenceService.operationPathMatchesPrefix(
                "GET /api/orders/1", "/api/orders"));
        assertFalse(TraceSpanPersistenceService.operationPathMatchesPrefix(
                "GET /api/users", "/api/orders"));
        assertTrue(TraceSpanPersistenceService.operationPathMatchesPrefix(
                "POST /api/orders", "/api/orders"));
    }

    @Test
    void pickRootWhenAllSpansHaveRemoteParent() {
        long now = System.currentTimeMillis();
        // 入站根带远程 parent（W3C 透传常见），子 Span parent 指向本 Trace
        TraceSpan entry = span("sca-order", "t-x", "POST /order/create", true, now - 200, 200);
        entry.setSpanId("root-span");
        entry.setParentSpanId("remote-from-gateway");
        entry.setSpanKind("SERVER");
        TraceSpan child = span("sca-order", "t-x", "GET /product/1", true, now - 100, 80);
        child.setSpanId("child-span");
        child.setParentSpanId("root-span");
        child.setSpanKind("CLIENT");

        TraceSpanPersistenceService persistence = persistence(entry, child);
        List<Map<String, Object>> rows = persistence.getRecentTraceSummaries(
                24, 10, null, "all", null, 0, null);
        assertEquals(1, rows.size());
        assertEquals("POST /order/create", rows.get(0).get("operationName"));
        assertEquals("sca-order", rows.get(0).get("serviceName"));
    }

    @Test
    void pickRootPrefersServerWhenBothHaveRemoteParent() {
        long now = System.currentTimeMillis();
        TraceSpan client = span("sca-order", "t-y", "GET sca-product/product/price/1", true, now - 200, 215);
        client.setSpanId("client-1");
        client.setParentSpanId("missing-server");
        client.setSpanKind("CLIENT");
        TraceSpan server = span("sca-order", "t-y", "POST /order/create", true, now - 199, 670);
        server.setSpanId("server-1");
        server.setParentSpanId("remote-from-gateway");
        server.setSpanKind("SERVER");

        TraceSpanPersistenceService persistence = persistence(client, server);
        List<Map<String, Object>> rows = persistence.getRecentTraceSummaries(
                24, 10, null, "all", null, 0, null);
        assertEquals(1, rows.size());
        assertEquals("POST /order/create", rows.get(0).get("operationName"));
    }

    @Test
    void serviceDetailSummaryAggregatesKpisAndSlowOps() {
        long now = System.currentTimeMillis();
        TraceSpan fast = span("sca-order", "t1", "GET /order/ping", true, now - 1_000, 20);
        TraceSpan slow = span("sca-order", "t2", "GET /order/create", true, now - 2_000, 500);
        TraceSpan err = span("sca-order", "t3", "GET /order/create", false, now - 3_000, 800);
        err.setRemoteService("sca-product");
        TraceSpan other = span("sca-user", "t4", "GET /user/score/1", true, now - 500, 40);

        TraceSpanPersistenceService persistence = persistence(fast, slow, err, other);
        Map<String, Object> summary = persistence.getServiceDetailSummary("sca-order", 24, 10, 5);

        assertEquals(true, summary.get("found"));
        @SuppressWarnings("unchecked")
        Map<String, Object> kpis = (Map<String, Object>) summary.get("kpis");
        assertEquals(3, ((Number) kpis.get("span_count")).intValue());
        assertEquals(1, ((Number) kpis.get("error_count")).intValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slowOps = (List<Map<String, Object>>) summary.get("slowOperations");
        assertFalse(slowOps.isEmpty());
        assertEquals("GET /order/create", slowOps.get(0).get("operationName"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outbound = (List<Map<String, Object>>) summary.get("outbound");
        assertEquals(1, outbound.size());
        assertEquals("sca-product", outbound.get(0).get("target_service"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recent = (List<Map<String, Object>>) summary.get("recentTraces");
        assertEquals(3, recent.size());
    }

    @Test
    void pathPrefixFiltersRecentSummaries() {
        long now = System.currentTimeMillis();
        TraceSpanPersistenceService persistence = persistence(
                span("order", "t-order", "GET /api/orders/9", true, now - 1_000, 120),
                span("user", "t-user", "GET /api/users/1", true, now - 2_000, 80),
                span("order", "t-err", "POST /api/orders", false, now - 3_000, 900)
        );

        List<Map<String, Object>> byPath = persistence.getRecentTraceSummaries(
                24, 50, null, "all", null, 0, "/api/orders");
        assertEquals(2, byPath.size());
        assertTrue(byPath.stream().allMatch(r ->
                String.valueOf(r.get("operationName")).contains("/api/orders")));

        List<Map<String, Object>> combo = persistence.getRecentTraceSummaries(
                24, 50, "order", "error", null, 500, "/api/orders");
        assertEquals(1, combo.size());
        assertEquals("t-err", combo.get(0).get("traceId"));
    }

    private static TraceSpanPersistenceService persistence(TraceSpan... spans) {
        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        InMemorySpanStore store = new InMemorySpanStore(storage, "memory");
        TraceSpanPersistenceService persistence = new TraceSpanPersistenceService(store);
        persistence.saveTraceSpans(List.of(spans));
        return persistence;
    }

    private static TraceSpan span(
            String service, String traceId, String op, boolean ok, long startMs, long durationMs) {
        TraceSpan s = new TraceSpan();
        s.setServiceName(service);
        s.setTraceId(traceId);
        s.setSpanId(traceId + "-root");
        s.setParentSpanId(null);
        s.setSuccess(ok);
        s.setStatusCode(ok ? "OK" : "ERROR");
        s.setStartTime(startMs);
        s.setEndTime(startMs + durationMs);
        s.setDurationMs(durationMs);
        s.setOperationName(op);
        return s;
    }
}
