package io.github.iweidujiang.springinsight.collector.controller;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.InsightServerApplication;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 不带 offset 时链路列表仍是数组；带上 offset 后返回分页对象。
 */
@SpringBootTest(classes = InsightServerApplication.class)
@AutoConfigureMockMvc
class TraceListPageApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpanStore spanStore;

    @Test
    void recentTracesKeepArrayUntilOffsetIsPresent() throws Exception {
        spanStore.saveAll(sample());

        mockMvc.perform(get("/api/v1/ui/traces/recent")
                        .param("hours", "1")
                        .param("service", "page-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].traceId").exists())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/v1/ui/traces/recent")
                        .param("hours", "1")
                        .param("service", "page-api")
                        .param("offset", "0")
                        .param("limit", "10")
                        .param("sort", "duration_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.data[0].traceId").value("t12"))
                .andExpect(jsonPath("$.data[0].durationMs").value(120));
    }

    private static List<TraceSpan> sample() {
        List<TraceSpan> spans = new ArrayList<>();
        long start = System.currentTimeMillis() - 60_000;
        for (int i = 1; i <= 12; i++) {
            TraceSpan span = new TraceSpan();
            span.setTraceId("t" + i);
            span.setSpanId("api-" + i);
            span.setServiceName("page-api");
            span.setOperationName("GET /page/" + i);
            span.setStartTime(start + i * 1000L);
            span.setDurationMs(i * 10L);
            span.setSuccess(true);
            spans.add(span);
        }
        return spans;
    }
}
