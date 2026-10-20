package io.github.iweidujiang.springinsight.collector.controller;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.InsightServerApplication;
import io.github.iweidujiang.springinsight.server.export.TraceCsvExporter;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 链路 CSV 导出接口：响应头、BOM 与内容。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
@SpringBootTest(classes = InsightServerApplication.class)
@AutoConfigureMockMvc
class TraceExportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpanStore spanStore;

    @Test
    void exportTracesCsvWithBomAndFilename() throws Exception {
        spanStore.saveAll(List.of(span("export-t1", "export-svc", "GET /hello", false, 80L)));

        MvcResult result = mockMvc.perform(get("/api/v1/ui/export/traces")
                        .param("hours", "1")
                        .param("service", "export-svc")
                        .param("status", "ALL")
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern(
                                "attachment; filename=\"traces-\\d{8}-\\d{6}\\.csv\"")))
                .andReturn();

        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(body.charAt(0) == TraceCsvExporter.UTF8_BOM);
        assertTrue(body.contains("export-t1"));
        assertTrue(body.contains("GET /hello"));
        assertTrue(body.contains("export-svc"));
        assertTrue(body.contains(",80,OK,1,"));
    }

    @Test
    void rejectUnknownFormat() throws Exception {
        mockMvc.perform(get("/api/v1/ui/export/traces").param("format", "xlsx"))
                .andExpect(status().isBadRequest());
    }

    private static TraceSpan span(String traceId, String service, String op, boolean error, long durationMs) {
        TraceSpan span = new TraceSpan();
        span.setTraceId(traceId);
        span.setSpanId(traceId + "-s");
        span.setServiceName(service);
        span.setOperationName(op);
        span.setStartTime(System.currentTimeMillis());
        span.setDurationMs(durationMs);
        span.setSuccess(!error);
        span.setStatusCode(error ? "ERROR" : "OK");
        return span;
    }
}
