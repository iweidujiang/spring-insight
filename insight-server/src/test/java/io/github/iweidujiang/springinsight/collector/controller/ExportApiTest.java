package io.github.iweidujiang.springinsight.collector.controller;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.InsightServerApplication;
import io.github.iweidujiang.springinsight.server.export.CsvExportSupport;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 错误分析 / 服务汇总 / 仪表盘导出接口。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
@SpringBootTest(classes = InsightServerApplication.class)
@AutoConfigureMockMvc
class ExportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpanStore spanStore;

    @Test
    void exportErrorsCsvWithBomAndPrimaryTypes() throws Exception {
        spanStore.saveAll(List.of(
                okSpan("err-ok", "err-svc", 10L),
                statusError("err-500", "err-svc", "500", 20L),
                exceptionError("err-npe", "err-svc", "java.lang.NullPointerException: boom", 30L)
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/ui/export/errors")
                        .param("hours", "1")
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern(
                                "attachment; filename=\"errors-\\d{8}-\\d{6}\\.csv\"")))
                .andReturn();

        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(body.charAt(0) == CsvExportSupport.UTF8_BOM);
        assertTrue(body.contains("服务名,总调用,错误数,错误率(%)"));
        assertTrue(body.contains("err-svc"));
        assertTrue(body.contains("HTTP 500"));
        assertTrue(body.contains("NullPointerException"));
    }

    @Test
    void exportServicesCsvWithLatencyColumns() throws Exception {
        spanStore.saveAll(List.of(
                okSpan("svc-t1", "latency-svc", 100L),
                okSpan("svc-t2", "latency-svc", 200L)
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/ui/export/services")
                        .param("hours", "1")
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern(
                                "attachment; filename=\"services-\\d{8}-\\d{6}\\.csv\"")))
                .andReturn();

        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(body.charAt(0) == CsvExportSupport.UTF8_BOM);
        assertTrue(body.contains("服务名,Span数,错误数,错误率(%),平均耗时(ms),p95耗时(ms),最大耗时(ms)"));
        assertTrue(body.contains("latency-svc"));
    }

    @Test
    void exportDashboardJsonSnapshot() throws Exception {
        spanStore.saveAll(List.of(okSpan("dash-t1", "dash-svc", 55L)));

        mockMvc.perform(get("/api/v1/ui/export/dashboard")
                        .param("hours", "1")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern(
                                "attachment; filename=\"dashboard-\\d{8}-\\d{6}\\.json\"")))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.hours").value(1))
                .andExpect(jsonPath("$.kpis").exists())
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.tops.slowServices").isArray())
                .andExpect(jsonPath("$.tops.errorServices").isArray())
                .andExpect(jsonPath("$.tops.recentTraces").isArray())
                .andExpect(jsonPath("$.period").exists());
    }

    @Test
    void rejectWrongFormat() throws Exception {
        mockMvc.perform(get("/api/v1/ui/export/errors").param("format", "json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/ui/export/services").param("format", "xlsx"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/ui/export/dashboard").param("format", "csv"))
                .andExpect(status().isBadRequest());
    }

    private static TraceSpan okSpan(String traceId, String service, long durationMs) {
        TraceSpan span = new TraceSpan();
        span.setTraceId(traceId);
        span.setSpanId(traceId + "-s");
        span.setServiceName(service);
        span.setOperationName("GET /ok");
        span.setStartTime(System.currentTimeMillis());
        span.setDurationMs(durationMs);
        span.setSuccess(true);
        span.setStatusCode("OK");
        return span;
    }

    private static TraceSpan statusError(String traceId, String service, String httpCode, long durationMs) {
        TraceSpan span = okSpan(traceId, service, durationMs);
        span.setSuccess(false);
        span.setStatusCode("ERROR");
        Map<String, String> tags = new HashMap<>();
        tags.put("http.status_code", httpCode);
        span.setTags(tags);
        return span;
    }

    private static TraceSpan exceptionError(String traceId, String service, String message, long durationMs) {
        TraceSpan span = okSpan(traceId, service, durationMs);
        span.setSuccess(false);
        span.setStatusCode("ERROR");
        span.setErrorCode("EXCEPTION");
        span.setErrorMessage(message);
        return span;
    }
}
