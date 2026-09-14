package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TraceContextExportService} 单元测试。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class TraceContextExportServiceTest {

    private TraceContextExportService exportService;
    private InMemorySpanStore store;

    @BeforeEach
    void setUp() {
        InsightServerStorageProperties props = new InsightServerStorageProperties();
        props.setMaxSpans(1000);
        store = new InMemorySpanStore(props, "memory");
        exportService = new TraceContextExportService(store);
    }

    @Test
    void missingTraceReturnsNull() {
        assertNull(exportService.buildContext("no-such-trace"));
    }

    @Test
    void buildsSummaryPathHintsAndSanitizedSpans() {
        long t0 = 1_000_000L;
        TraceSpan gateway = span("tid-1", "g1", null, "sca-gateway", "GET /order/create",
                "SERVER", t0, 200L, true, null, null);
        TraceSpan order = span("tid-1", "o1", "g1", "sca-order", "GET /order/create",
                "SERVER", t0 + 10, 150L, false, "HTTP_500", "HTTP Status: 500");
        order.addTag("http.status_code", "500");
        order.setRemoteService("sca-product");
        TraceSpan feign = span("tid-1", "f1", "o1", "sca-order", "GET sca-product/price",
                "CLIENT", t0 + 20, 120L, false, "HTTP_503", "HTTP Status: 503");
        feign.addTag("http.status_code", "503");
        feign.setRemoteService("sca-product");

        store.saveAll(List.of(gateway, order, feign));

        Map<String, Object> ctx = exportService.buildContext("tid-1");
        assertNotNull(ctx);
        assertEquals(TraceContextExportService.SCHEMA_VERSION, ctx.get("schemaVersion"));
        assertEquals("tid-1", ctx.get("traceId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) ctx.get("summary");
        assertEquals("sca-gateway", summary.get("rootService"));
        assertEquals(3, summary.get("spanCount"));
        assertEquals(2, summary.get("errorCount"));
        assertFalse((Boolean) summary.get("success"));
        assertEquals(200L, ((Number) summary.get("durationMs")).longValue());

        @SuppressWarnings("unchecked")
        List<String> path = (List<String>) ctx.get("path");
        assertTrue(path.contains("sca-gateway"));
        assertTrue(path.contains("sca-order"));
        assertTrue(path.contains("sca-product"));

        @SuppressWarnings("unchecked")
        Map<String, Object> hints = (Map<String, Object>) ctx.get("hints");
        assertEquals("g1", hints.get("slowestSpanId"));
        assertEquals("o1", hints.get("firstErrorSpanId"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> spans = (List<Map<String, Object>>) ctx.get("spans");
        assertEquals(3, spans.size());
        Map<String, Object> errRow = spans.stream()
                .filter(r -> "o1".equals(r.get("spanId")))
                .findFirst()
                .orElseThrow();
        assertEquals("500", errRow.get("statusCode"));
        assertEquals(false, errRow.get("success"));
        assertEquals("HTTP Status: 500", errRow.get("errorMessage"));
    }

    @Test
    void truncatesLongErrorMessage() {
        String longMsg = "x".repeat(500);
        TraceSpan span = span("tid-2", "s1", null, "demo", "op", "SERVER",
                System.currentTimeMillis(), 10L, false, "EXCEPTION", longMsg);
        Map<String, Object> ctx = exportService.buildFromSpans("tid-2", List.of(span));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> spans = (List<Map<String, Object>>) ctx.get("spans");
        String msg = (String) spans.getFirst().get("errorMessage");
        assertTrue(msg.length() < 500);
        assertTrue(msg.endsWith("..."));
    }

    private static TraceSpan span(String traceId, String spanId, String parent,
                                  String service, String op, String kind,
                                  long start, long dur, boolean ok,
                                  String errorCode, String errorMessage) {
        TraceSpan s = new TraceSpan();
        s.setTraceId(traceId);
        s.setSpanId(spanId);
        s.setParentSpanId(parent);
        s.setServiceName(service);
        s.setOperationName(op);
        s.setSpanKind(kind);
        s.setStartTime(start);
        s.setEndTime(start + dur);
        s.setDurationMs(dur);
        if (ok) {
            s.setSuccess(true);
            s.setStatusCode("OK");
        } else {
            s.setSuccess(false);
            s.setStatusCode("ERROR");
            s.setErrorCode(errorCode);
            s.setErrorMessage(errorMessage);
        }
        return s;
    }
}
