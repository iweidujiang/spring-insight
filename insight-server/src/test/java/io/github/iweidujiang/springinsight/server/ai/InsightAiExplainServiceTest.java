package io.github.iweidujiang.springinsight.server.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerAiProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettingsService;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import io.github.iweidujiang.springinsight.storage.service.TraceContextExportService;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 解释：截断、降级与 Chat Completions 成功路径。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class InsightAiExplainServiceTest {

    private HttpServer server;

    /**
     * 关闭 mock LLM。
     */
    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 未启用时 degraded，不调 HTTP。
     */
    @Test
    void disabledReturnsDegraded() {
        InsightAiExplainService svc = service(props(false, "http://127.0.0.1:1/v1", "k"), storeWithTrace());
        Map<String, Object> body = svc.explain("t1");
        assertNotNull(body);
        assertTrue((Boolean) body.get("degraded"));
        assertTrue(String.valueOf(body.get("markdown")).contains("AI 建议"));
    }

    /**
     * 无 key 时 degraded。
     */
    @Test
    void missingKeyReturnsDegraded() {
        InsightServerAiProperties p = props(true, "https://api.deepseek.com/v1", "");
        InsightAiExplainService svc = service(p, storeWithTrace());
        Map<String, Object> body = svc.explain("t1");
        assertTrue((Boolean) body.get("degraded"));
    }

    /**
     * Chat Completions 2xx 返回 markdown。
     *
     * @throws Exception mock 失败
     */
    @Test
    void explainSuccessViaCompatibleApi() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        int port = startMockLlm(hits, 200,
                "{\"choices\":[{\"message\":{\"content\":\"## 结论\\n下游超时\"}}]}");

        InsightServerAiProperties p = props(true, "http://127.0.0.1:" + port + "/v1", "sk-test");
        p.setModel("deepseek-chat");
        p.setProvider("deepseek");
        InsightAiExplainService svc = service(p, storeWithTrace());

        Map<String, Object> body = svc.explain("t1");
        assertEquals(1, hits.get());
        assertFalse((Boolean) body.get("degraded"));
        assertTrue(String.valueOf(body.get("markdown")).contains("下游超时"));
        assertTrue(String.valueOf(body.get("markdown")).contains("AI 建议"));
        assertEquals("deepseek-chat", body.get("model"));
    }

    /**
     * 上游 500 降级不抛。
     *
     * @throws Exception mock 失败
     */
    @Test
    void upstreamErrorDegrades() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        int port = startMockLlm(hits, 500, "{\"error\":\"boom\"}");
        InsightServerAiProperties p = props(true, "http://127.0.0.1:" + port + "/v1", "sk-test");
        InsightAiExplainService svc = service(p, storeWithTrace());
        Map<String, Object> body = svc.explain("t1");
        assertEquals(1, hits.get());
        assertTrue((Boolean) body.get("degraded"));
    }

    /**
     * Span 截断保留错误并打 truncated。
     */
    @Test
    @SuppressWarnings("unchecked")
    void truncateKeepsErrors() {
        InsightAiExplainService svc = service(props(false, "", ""), storeWithTrace());
        Map<String, Object> ctx = Map.of(
                "traceId", "t",
                "spans", List.of(
                        Map.of("spanId", "ok1", "success", true),
                        Map.of("spanId", "err", "success", false),
                        Map.of("spanId", "ok2", "success", true),
                        Map.of("spanId", "ok3", "success", true)
                )
        );
        Map<String, Object> truncated = svc.truncateContext(ctx, 2);
        assertTrue(Boolean.TRUE.equals(truncated.get("truncated")));
        List<Map<String, Object>> spans = (List<Map<String, Object>>) truncated.get("spans");
        assertEquals(2, spans.size());
        assertEquals("err", spans.getFirst().get("spanId"));
    }

    private static InsightServerAiProperties props(boolean enabled, String baseUrl, String key) {
        InsightServerAiProperties p = new InsightServerAiProperties();
        p.setEnabled(enabled);
        p.setBaseUrl(baseUrl);
        p.setApiKey(key);
        p.setProvider("openai-compatible");
        p.setModel("gpt-4o-mini");
        p.setTimeoutMs(5000);
        p.setMaxTokens(200);
        p.setMaxInputSpans(40);
        return p;
    }

    private static InsightAiExplainService service(InsightServerAiProperties props, TraceContextExportService export) {
        InsightRuntimeSettingsService settings = mock(InsightRuntimeSettingsService.class);
        when(settings.effectiveAi()).thenReturn(props);
        TraceSpanPersistenceService persistence = mock(TraceSpanPersistenceService.class);
        when(persistence.findErrorBreakdown(org.mockito.ArgumentMatchers.anyInt())).thenReturn(Map.of(
                "total_error_spans", 0,
                "by_service", List.of(),
                "by_status_code", List.of(),
                "by_exception", List.of()
        ));
        return new InsightAiExplainService(settings, export, persistence, new InsightAiAuditLog(), new ObjectMapper());
    }

    private static TraceContextExportService storeWithTrace() {
        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        InMemorySpanStore store = new InMemorySpanStore(storage, "memory");
        TraceSpan span = new TraceSpan();
        span.setTraceId("t1");
        span.setSpanId("s1");
        span.setServiceName("sca-order");
        span.setOperationName("GET /order");
        span.setSuccess(false);
        span.setStatusCode("ERROR");
        span.setErrorMessage("HTTP Status: 500");
        span.setStartTime(System.currentTimeMillis());
        store.saveAll(List.of(span));
        return new TraceContextExportService(store);
    }

    private int startMockLlm(AtomicInteger hits, int status, String json) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            hits.incrementAndGet();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server.getAddress().getPort();
    }
}
