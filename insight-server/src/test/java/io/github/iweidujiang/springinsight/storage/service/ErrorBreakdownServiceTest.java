package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误 breakdown 聚合测试。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class ErrorBreakdownServiceTest {

    private TraceSpanPersistenceService persistenceService;

    /**
     * 使用内存存储装配门面。
     */
    @BeforeEach
    void setUp() {
        InsightServerStorageProperties props = new InsightServerStorageProperties();
        InMemorySpanStore store = new InMemorySpanStore(props, "memory");
        persistenceService = new TraceSpanPersistenceService(store);
    }

    /**
     * 404 与 NPE 应分别进入 status / exception 桶。
     */
    @Test
    void breakdownGroupsByStatusAndException() {
        TraceSpan http404 = errorSpan("order", "t1");
        http404.setErrorCode("HTTP_404");
        http404.setErrorMessage("HTTP Status: 404");
        http404.setTags(Map.of("http.status_code", "404"));

        TraceSpan npe = errorSpan("user", "t2");
        npe.setErrorCode("EXCEPTION");
        npe.setErrorMessage("java.lang.NullPointerException: boom");

        persistenceService.saveTraceSpans(List.of(http404, npe));

        Map<String, Object> body = persistenceService.findErrorBreakdown(24);
        assertEquals(2L, ((Number) body.get("total_error_spans")).longValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> status = (List<Map<String, Object>>) body.get("by_status_code");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exceptions = (List<Map<String, Object>>) body.get("by_exception");

        assertEquals(1, status.size());
        assertEquals("404", status.get(0).get("key"));
        assertEquals(1, exceptions.size());
        assertEquals("NullPointerException", exceptions.get(0).get("key"));
        assertTrue(((Number) body.get("hours")).intValue() == 24
                || body.containsKey("by_service"));
    }

    private static TraceSpan errorSpan(String service, String traceId) {
        TraceSpan span = new TraceSpan();
        span.setTraceId(traceId);
        span.setSpanId(traceId + "-s");
        span.setServiceName(service);
        span.setSuccess(false);
        span.setStatusCode("ERROR");
        span.setStartTime(System.currentTimeMillis());
        span.setEndTime(System.currentTimeMillis());
        span.setDurationMs(1L);
        return span;
    }
}
