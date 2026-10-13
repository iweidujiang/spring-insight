package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TroubleshootPackExportService} 单元测试。
 *
 * @since 2026-10-13
 */
class TroubleshootPackExportServiceTest {

    private InMemorySpanStore store;
    private TroubleshootPackExportService packExport;

    @BeforeEach
    void setUp() {
        InsightServerStorageProperties props = new InsightServerStorageProperties();
        props.setMaxSpans(1000);
        store = new InMemorySpanStore(props, "memory");
        TraceContextExportService contextExport = new TraceContextExportService(store);
        TraceSpanPersistenceService persistence = new TraceSpanPersistenceService(store);
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.insight.server.version", "1.0.0-test");
        packExport = new TroubleshootPackExportService(contextExport, persistence, env, null);
    }

    @Test
    void missingTraceReturnsNull() {
        assertNull(packExport.buildPack("missing", 24));
    }

    @Test
    void buildsPackWithServerWindowAndTrace() {
        long now = System.currentTimeMillis();
        TraceSpan root = span("tid-pack", "s1", null, "demo", "GET /api", true, now - 1_000, 80);
        TraceSpan jdbc = span("tid-pack", "s2", "s1", "demo", "DB SELECT", true, now - 900, 40);
        jdbc.setComponent("JDBC");
        jdbc.setRemoteService("h2:mem:demo");
        store.saveAll(List.of(root, jdbc));

        Map<String, Object> pack = packExport.buildPack("tid-pack", 24);
        assertNotNull(pack);
        assertEquals(TroubleshootPackExportService.SCHEMA_VERSION, pack.get("schemaVersion"));
        assertEquals("troubleshoot-pack", pack.get("kind"));
        assertNotNull(pack.get("exportedAt"));

        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) pack.get("server");
        assertEquals("1.0.0-test", server.get("version"));

        @SuppressWarnings("unchecked")
        Map<String, Object> window = (Map<String, Object>) pack.get("window");
        assertEquals(24, window.get("hours"));
        assertTrue(((Number) window.get("spanCount")).intValue() >= 2);

        @SuppressWarnings("unchecked")
        Map<String, Object> trace = (Map<String, Object>) pack.get("trace");
        assertEquals("tid-pack", trace.get("traceId"));
        assertEquals(TraceContextExportService.SCHEMA_VERSION, trace.get("schemaVersion"));
    }

    private static TraceSpan span(String traceId, String spanId, String parent, String service,
                                  String op, boolean success, long start, long dur) {
        TraceSpan s = new TraceSpan(traceId, parent);
        s.setSpanId(spanId);
        s.setServiceName(service);
        s.setOperationName(op);
        s.setSpanKind("SERVER");
        s.setStartTime(start);
        s.setEndTime(start + dur);
        s.setDurationMs(dur);
        s.setSuccess(success);
        s.setStatusCode(success ? "OK" : "ERROR");
        s.setSampled(true);
        return s;
    }
}
