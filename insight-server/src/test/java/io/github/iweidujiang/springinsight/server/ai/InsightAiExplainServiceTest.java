package io.github.iweidujiang.springinsight.server.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerAiProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.server.insight.InsightPeriodInsightService;
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
     * 状态接口诚实返回标签与原因。
     */
    @Test
    void statusReportsLabelAndReason() {
        InsightAiExplainService off = service(props(false, "https://api.example.com/v1", "k"), storeWithTrace());
        Map<String, Object> disabled = off.status();
        assertFalse((Boolean) disabled.get("invokeReady"));
        assertEquals("未启用", disabled.get("statusLabel"));
        assertTrue(String.valueOf(disabled.get("reason")).contains("未启用"));

        InsightAiExplainService ready = service(props(true, "https://api.example.com/v1", "k"), storeWithTrace());
        Map<String, Object> ok = ready.status();
        assertTrue((Boolean) ok.get("invokeReady"));
        assertEquals("就绪", ok.get("statusLabel"));
        assertTrue(String.valueOf(ok.get("reason")).contains("gpt-4o-mini"));

        InsightServerAiProperties noKey = props(true, "https://api.example.com/v1", "");
        Map<String, Object> blocked = service(noKey, storeWithTrace()).status();
        assertEquals("未就绪", blocked.get("statusLabel"));
        assertTrue(String.valueOf(blocked.get("reason")).contains("api-key"));
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
     * Trace 对比：AI 未启用返回 statusLabel=未启用。
     */
    @Test
    void compareTraces_disabledReturnsStatusLabel() {
        InMemorySpanStore store = twoTracesStore();
        InsightAiExplainService svc = serviceWithStore(props(false, "http://127.0.0.1:1/v1", "k"), store);
        Map<String, Object> body = svc.explainCompareTraces("tA", "tB");
        assertNotNull(body);
        assertTrue(Boolean.TRUE.equals(body.get("degraded")));
        assertEquals("未启用", body.get("statusLabel"));
        assertEquals("compare-traces", body.get("kind"));
    }

    /**
     * Trace 对比：有显著差异时经 Chat Completions 返回结构化结论与耗时证据。
     *
     * @throws Exception mock 失败
     */
    @Test
    @SuppressWarnings("unchecked")
    void compareTraces_successViaCompatibleApi() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        int port = startMockLlm(hits, 200,
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"Trace B 比 A 慢约 200ms，主要是 GET /order\\\",\\\"evidence\\\":[{\\\"span\\\":\\\"GET /order\\\",\\\"service\\\":\\\"order-db\\\",\\\"analysis\\\":\\\"下游变慢\\\"}],\\\"suggestions\\\":[\\\"查 order-db 慢查询\\\"]}\"}}]}");

        InsightServerAiProperties p = props(true, "http://127.0.0.1:" + port + "/v1", "sk-test");
        p.setModel("deepseek-chat");
        p.setProvider("deepseek");
        InsightAiExplainService svc = serviceWithStore(p, twoTracesStore());

        Map<String, Object> body = svc.explainCompareTraces("tA", "tB");
        assertEquals(1, hits.get());
        assertFalse(Boolean.TRUE.equals(body.get("degraded")));
        assertEquals("就绪", body.get("statusLabel"));
        assertTrue(String.valueOf(body.get("summary")).contains("慢"));
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) body.get("evidence");
        assertFalse(evidence.isEmpty());
        assertEquals("GET /order", evidence.get(0).get("span"));
        assertEquals(100L, ((Number) evidence.get(0).get("durationA")).longValue());
        assertEquals(300L, ((Number) evidence.get(0).get("durationB")).longValue());
        assertEquals(200L, ((Number) evidence.get(0).get("delta")).longValue());
        assertTrue(String.valueOf(evidence.get(0).get("analysis")).contains("下游"));
    }

    /**
     * Trace 对比：上游失败返回 statusLabel=失败。
     *
     * @throws Exception mock 失败
     */
    @Test
    void compareTraces_upstreamErrorStatusFailed() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        int port = startMockLlm(hits, 500, "{\"error\":\"boom\"}");
        InsightServerAiProperties p = props(true, "http://127.0.0.1:" + port + "/v1", "sk-test");
        InsightAiExplainService svc = serviceWithStore(p, twoTracesStore());
        Map<String, Object> body = svc.explainCompareTraces("tA", "tB");
        assertEquals(1, hits.get());
        assertTrue(Boolean.TRUE.equals(body.get("degraded")));
        assertEquals("失败", body.get("statusLabel"));
        assertTrue(String.valueOf(body.get("reason")).contains("失败")
                || String.valueOf(body.get("message")).contains("失败"));
    }

    /**
     * 变更归因：AI 未启用返回降级，但仍带本地 changes。
     */
    @Test
    @SuppressWarnings("unchecked")
    void changeAttribution_disabledReturnsDegradedWithChanges() {
        InsightAiExplainService svc = serviceWithStore(
                props(false, "http://127.0.0.1:1/v1", "k"), changeAttributionStore());
        Map<String, Object> body = svc.explainChangeAttribution(2, 2);
        assertTrue(Boolean.TRUE.equals(body.get("degraded")));
        assertEquals("未启用", body.get("statusLabel"));
        assertEquals("change-attribution", body.get("kind"));
        List<Map<String, Object>> changes = (List<Map<String, Object>>) body.get("changes");
        assertNotNull(changes);
        assertTrue(changes.stream().anyMatch(c -> "error_rate".equals(c.get("metric"))
                && "sca-user".equals(c.get("service"))));
    }

    /**
     * 变更归因：有显著差异时经 Chat Completions 返回结构化结论。
     *
     * @throws Exception mock 失败
     */
    @Test
    @SuppressWarnings("unchecked")
    void changeAttribution_successViaCompatibleApi() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        int port = startMockLlm(hits, 200,
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"上线后 sca-user 错误率从 0% 升至 100%\\\",\\\"possibleCauses\\\":[\\\"发布引入空指针\\\"],\\\"suggestions\\\":[\\\"查 sca-user 最近错误 Trace\\\"]}\"}}]}");

        InsightServerAiProperties p = props(true, "http://127.0.0.1:" + port + "/v1", "sk-test");
        p.setModel("deepseek-chat");
        p.setProvider("deepseek");
        InsightAiExplainService svc = serviceWithStore(p, changeAttributionStore());

        Map<String, Object> body = svc.explainChangeAttribution(2, 2);
        assertEquals(1, hits.get());
        assertFalse(Boolean.TRUE.equals(body.get("degraded")));
        assertEquals("就绪", body.get("statusLabel"));
        assertTrue(String.valueOf(body.get("summary")).contains("sca-user"));
        List<Map<String, Object>> changes = (List<Map<String, Object>>) body.get("changes");
        assertFalse(changes.isEmpty());
        List<String> causes = (List<String>) body.get("possibleCauses");
        assertTrue(causes.stream().anyMatch(s -> s.contains("空指针")));
        List<String> suggestions = (List<String>) body.get("suggestions");
        assertTrue(suggestions.stream().anyMatch(s -> s.contains("错误")));
    }

    /**
     * 变更归因：上游失败降级，仍返回事实 changes。
     *
     * @throws Exception mock 失败
     */
    @Test
    @SuppressWarnings("unchecked")
    void changeAttribution_upstreamErrorDegrades() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        int port = startMockLlm(hits, 500, "{\"error\":\"boom\"}");
        InsightServerAiProperties p = props(true, "http://127.0.0.1:" + port + "/v1", "sk-test");
        InsightAiExplainService svc = serviceWithStore(p, changeAttributionStore());
        Map<String, Object> body = svc.explainChangeAttribution(2, 2);
        assertEquals(1, hits.get());
        assertTrue(Boolean.TRUE.equals(body.get("degraded")));
        assertEquals("失败", body.get("statusLabel"));
        List<Map<String, Object>> changes = (List<Map<String, Object>>) body.get("changes");
        assertFalse(changes.isEmpty());
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
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"下游超时\\\",\\\"evidence\\\":[{\\\"type\\\":\\\"span\\\",\\\"ref\\\":\\\"s1\\\",\\\"label\\\":\\\"GET /order\\\",\\\"reason\\\":\\\"500\\\"}],\\\"suggestions\\\":[\\\"查下游\\\"]}\"}}]}");

        InsightServerAiProperties p = props(true, "http://127.0.0.1:" + port + "/v1", "sk-test");
        p.setModel("deepseek-chat");
        p.setProvider("deepseek");
        InsightAiExplainService svc = service(p, storeWithTrace());

        Map<String, Object> body = svc.explain("t1");
        assertEquals(1, hits.get());
        assertFalse((Boolean) body.get("degraded"));
        assertEquals(1, body.get("schemaVersion"));
        assertEquals("下游超时", body.get("summary"));
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
     * 告警未开 attachToAlerts 时跳过，不调 HTTP。
     */
    @Test
    void explainForAlertSkippedWhenDisabled() {
        InsightServerAiProperties p = props(true, "http://127.0.0.1:1/v1", "k");
        p.setAttachToAlerts(false);
        InsightAiExplainService svc = service(p, storeWithTrace());
        Map<String, Object> body = svc.explainForAlert("sca-order", "error_rate", 20, 10, 15);
        assertTrue(Boolean.TRUE.equals(body.get("skipped")));
        assertFalse(Boolean.TRUE.equals(body.get("aiAttached")));
    }

    /**
     * 每小时配额耗尽后拒绝。
     */
    @Test
    void alertQuotaLimited() {
        InsightAiExplainService svc = service(props(false, "", ""), storeWithTrace());
        assertTrue(svc.tryAcquireAlertQuota(2));
        assertTrue(svc.tryAcquireAlertQuota(2));
        assertFalse(svc.tryAcquireAlertQuota(2));
    }

    /**
     * 时段 AI 每小时配额耗尽后拒绝。
     */
    @Test
    void periodQuotaLimited() {
        InsightAiExplainService svc = service(props(false, "", ""), storeWithTrace());
        assertTrue(svc.tryAcquirePeriodQuota(2));
        assertTrue(svc.tryAcquirePeriodQuota(2));
        assertFalse(svc.tryAcquirePeriodQuota(2));
    }

    /**
     * 时段 AI：有数据时 Chat Completions 成功返回结构化小结。
     *
     * @throws Exception mock 失败
     */
    @Test
    void explainPeriodSuccessViaCompatibleApi() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        int port = startMockLlm(hits, 200,
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"近窗平稳\\\",\\\"evidence\\\":[{\\\"type\\\":\\\"errors\\\",\\\"ref\\\":\\\"\\\",\\\"label\\\":\\\"错误分析\\\",\\\"reason\\\":\\\"无新增错误\\\"}],\\\"suggestions\\\":[\\\"保持观察\\\"]}\"}}]}");

        InsightServerAiProperties p = props(true, "http://127.0.0.1:" + port + "/v1", "sk-test");
        p.setModel("deepseek-chat");
        p.setProvider("deepseek");
        p.setPeriodMaxPerHour(20);

        InsightPeriodInsightService period = mock(InsightPeriodInsightService.class);
        Map<String, Object> periodBody = new java.util.LinkedHashMap<>();
        periodBody.put("schemaVersion", 1);
        periodBody.put("hours", 24);
        periodBody.put("compare", true);
        periodBody.put("headline", "近 24 小时 10 条 Span，无错误");
        periodBody.put("current", Map.of(
                "spanCount", 10L,
                "errorSpanCount", 0L,
                "errorServices", List.of(),
                "slowServices", List.of(),
                "hotEdges", List.of(),
                "sampleErrorTraceIds", List.of(),
                "sampleSlowTraceIds", List.of()));
        periodBody.put("delta", Map.of("errorSpanCount", 0L, "newErrorServices", List.of()));
        when(period.build(org.mockito.ArgumentMatchers.anyInt())).thenReturn(periodBody);

        InsightRuntimeSettingsService settings = mock(InsightRuntimeSettingsService.class);
        when(settings.effectiveAi()).thenReturn(p);
        InsightAiExplainService svc = new InsightAiExplainService(
                settings, storeWithTrace(), mock(TraceSpanPersistenceService.class),
                period, new InsightAiAuditLog(), new ObjectMapper());

        Map<String, Object> body = svc.explainPeriod(24);
        assertEquals(1, hits.get());
        assertFalse(Boolean.TRUE.equals(body.get("degraded")));
        assertEquals("period", body.get("kind"));
        assertEquals("近窗平稳", body.get("summary"));
        assertEquals("近 24 小时 10 条 Span，无错误", body.get("factsHeadline"));
        assertTrue(Boolean.TRUE.equals(body.get("structured")));
    }

    /**
     * 时段 AI：有数据且配额耗尽时降级提示上限。
     */
    @Test
    void explainPeriodQuotaExceededDegrades() {
        InsightServerAiProperties p = props(true, "http://127.0.0.1:1/v1", "sk-test");
        p.setPeriodMaxPerHour(1);
        InsightPeriodInsightService period = mock(InsightPeriodInsightService.class);
        Map<String, Object> periodBody = new java.util.LinkedHashMap<>();
        periodBody.put("schemaVersion", 1);
        periodBody.put("hours", 24);
        periodBody.put("compare", true);
        periodBody.put("headline", "近 24 小时 10 条 Span，无错误");
        periodBody.put("current", Map.of(
                "spanCount", 10L,
                "errorSpanCount", 0L,
                "errorServices", List.of(),
                "slowServices", List.of(),
                "hotEdges", List.of(),
                "sampleErrorTraceIds", List.of(),
                "sampleSlowTraceIds", List.of()));
        periodBody.put("delta", Map.of("errorSpanCount", 0L));
        when(period.build(org.mockito.ArgumentMatchers.anyInt())).thenReturn(periodBody);

        InsightRuntimeSettingsService settings = mock(InsightRuntimeSettingsService.class);
        when(settings.effectiveAi()).thenReturn(p);
        TraceSpanPersistenceService persistence = mock(TraceSpanPersistenceService.class);
        InsightAiExplainService svc = new InsightAiExplainService(
                settings, storeWithTrace(), persistence, period, new InsightAiAuditLog(), new ObjectMapper());

        assertTrue(svc.tryAcquirePeriodQuota(1));
        Map<String, Object> body = svc.explainPeriod(24);
        assertTrue(Boolean.TRUE.equals(body.get("degraded")));
        assertTrue(String.valueOf(body.get("summary")).contains("上限"));
        assertEquals("近 24 小时 10 条 Span，无错误", body.get("factsHeadline"));
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
        assertEquals("err", spans.get(0).get("spanId"));
    }

    /**
     * content 为多段数组时仍能取出正文。
     *
     * @throws Exception 解析失败
     */
    @Test
    void extractContentFromArrayParts() throws Exception {
        InsightAiExplainService svc = service(props(false, "", ""), storeWithTrace());
        String body = """
                {"choices":[{"message":{"content":[{"type":"text","text":"{\\"summary\\":\\"ok\\"}"}]}}]}
                """;
        assertTrue(svc.extractContent(body).contains("summary"));
    }

    /**
     * 时段 AI：未启用时降级，summary 只写失败原因（不把事实 headline 塞进面板）。
     */
    @Test
    void explainPeriodDisabledUsesFactsHeadline() {
        InsightAiExplainService svc = service(props(false, "http://127.0.0.1:1/v1", "k"), storeWithTrace());
        Map<String, Object> body = svc.explainPeriod(24);
        assertTrue(Boolean.TRUE.equals(body.get("degraded")));
        assertEquals("period", body.get("kind"));
        assertEquals("近 24 小时暂无 Span", body.get("factsHeadline"));
        assertTrue(String.valueOf(body.get("summary")).contains("AI"));
        assertFalse(String.valueOf(body.get("summary")).equals("近 24 小时暂无 Span"));
    }

    /**
     * content 为空时，仅当 reasoning 含 summary JSON 才可用。
     *
     * @throws Exception 仅有思考草稿
     */
    @Test
    void extractContentFallsBackToReasoning() throws Exception {
        InsightAiExplainService svc = service(props(false, "", ""), storeWithTrace());
        String body = """
                {"choices":[{"finish_reason":"stop","message":{"content":null,"reasoning_content":"think... {\\"summary\\":\\"via-reasoning\\",\\"evidence\\":[],\\"suggestions\\":[]}"}}]}
                """;
        assertTrue(svc.extractContent(body).contains("via-reasoning"));
    }

    /**
     * 纯思考草稿（无 summary JSON）应失败，避免当成结论。
     */
    @Test
    void extractContentRejectsReasoningDraft() {
        InsightAiExplainService svc = service(props(false, "", ""), storeWithTrace());
        String body = """
                {"choices":[{"finish_reason":"length","message":{"content":null,"reasoning_content":"We need answer only JSON. Need infer. Provided edge sca-user -> sca-loyalty."}}]}
                """;
        try {
            svc.extractContent(body);
            org.junit.jupiter.api.Assertions.fail("expected failure");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("思考过程") || e.getMessage().contains("JSON"));
        }
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
        InsightPeriodInsightService period = mock(InsightPeriodInsightService.class);
        Map<String, Object> periodBody = new java.util.LinkedHashMap<>();
        periodBody.put("schemaVersion", 1);
        periodBody.put("hours", 24);
        periodBody.put("compare", true);
        periodBody.put("headline", "近 24 小时暂无 Span");
        periodBody.put("current", Map.of("spanCount", 0L, "errorSpanCount", 0L));
        periodBody.put("delta", null);
        when(period.build(org.mockito.ArgumentMatchers.anyInt())).thenReturn(periodBody);
        return new InsightAiExplainService(
                settings, export, persistence, period, new InsightAiAuditLog(), new ObjectMapper());
    }

    private static InsightAiExplainService serviceWithStore(InsightServerAiProperties props, InMemorySpanStore store) {
        InsightRuntimeSettingsService settings = mock(InsightRuntimeSettingsService.class);
        when(settings.effectiveAi()).thenReturn(props);
        TraceSpanPersistenceService persistence = new TraceSpanPersistenceService(store);
        InsightPeriodInsightService period = mock(InsightPeriodInsightService.class);
        when(period.build(org.mockito.ArgumentMatchers.anyInt())).thenReturn(Map.of(
                "schemaVersion", 1,
                "hours", 24,
                "headline", "",
                "current", Map.of("spanCount", 0L, "errorSpanCount", 0L)
        ));
        return new InsightAiExplainService(
                settings,
                new TraceContextExportService(store),
                persistence,
                period,
                new InsightAiAuditLog(),
                new ObjectMapper());
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

    /**
     * 上线前 2h 无错误、上线后 2h sca-user 全错；另有新增依赖边 order-api。
     */
    private static InMemorySpanStore changeAttributionStore() {
        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        InMemorySpanStore store = new InMemorySpanStore(storage, "memory");
        long now = System.currentTimeMillis();
        long hour = 3_600_000L;
        // before 窗：now-4h .. now-2h，全部成功
        TraceSpan beforeOk = new TraceSpan();
        beforeOk.setTraceId("tb1");
        beforeOk.setSpanId("b1");
        beforeOk.setServiceName("sca-user");
        beforeOk.setOperationName("GET /user");
        beforeOk.setDurationMs(30L);
        beforeOk.setStartTime(now - 3 * hour);
        beforeOk.setSuccess(true);
        beforeOk.setRemoteService("user-db");
        // after 窗：错误
        TraceSpan afterErr = new TraceSpan();
        afterErr.setTraceId("ta1");
        afterErr.setSpanId("a1");
        afterErr.setServiceName("sca-user");
        afterErr.setOperationName("GET /user");
        afterErr.setDurationMs(40L);
        afterErr.setStartTime(now - hour / 2);
        afterErr.setSuccess(false);
        afterErr.setStatusCode("ERROR");
        afterErr.setErrorMessage("NullPointerException");
        afterErr.setRemoteService("user-db");
        TraceSpan afterEdge = new TraceSpan();
        afterEdge.setTraceId("ta2");
        afterEdge.setSpanId("a2");
        afterEdge.setServiceName("sca-user");
        afterEdge.setOperationName("GET /profile");
        afterEdge.setDurationMs(20L);
        afterEdge.setStartTime(now - hour / 3);
        afterEdge.setSuccess(true);
        afterEdge.setRemoteService("order-api");
        store.saveAll(List.of(beforeOk, afterErr, afterEdge));
        return store;
    }

    /** 两条 Trace：同名 GET /order 耗时 100 vs 300（差异 &gt; 20%）。 */
    private static InMemorySpanStore twoTracesStore() {
        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        InMemorySpanStore store = new InMemorySpanStore(storage, "memory");
        TraceSpan a = new TraceSpan();
        a.setTraceId("tA");
        a.setSpanId("a1");
        a.setServiceName("sca-order");
        a.setOperationName("GET /order");
        a.setRemoteService("order-db");
        a.setDurationMs(100L);
        a.setStartTime(System.currentTimeMillis());
        a.setSuccess(true);
        TraceSpan b = new TraceSpan();
        b.setTraceId("tB");
        b.setSpanId("b1");
        b.setServiceName("sca-order");
        b.setOperationName("GET /order");
        b.setRemoteService("order-db");
        b.setDurationMs(300L);
        b.setStartTime(System.currentTimeMillis());
        b.setSuccess(true);
        store.saveAll(List.of(a, b));
        return store;
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
