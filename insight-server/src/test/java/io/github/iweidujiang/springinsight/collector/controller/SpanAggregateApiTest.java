package io.github.iweidujiang.springinsight.collector.controller;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.InsightServerApplication;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/ui/spans/aggregate} 返回服务级聚合 JSON。
 */
@SpringBootTest(classes = InsightServerApplication.class)
@AutoConfigureMockMvc
class SpanAggregateApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpanStore spanStore;

    @Test
    void aggregateReturnsServiceRowsAndHonorsServiceFilter() throws Exception {
        spanStore.saveAll(List.of(
                span("sca-user", "agg-a", 100, false),
                span("sca-user", "agg-b", 40, true),
                span("pay", "agg-c", 20, true)));

        mockMvc.perform(get("/api/v1/ui/spans/aggregate").param("hours", "1").param("service", "sca-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceName").value("sca-user"))
                .andExpect(jsonPath("$[0].spanCount").value(2))
                .andExpect(jsonPath("$[0].errorCount").value(1))
                .andExpect(jsonPath("$[0].avgDuration").value(70.0))
                .andExpect(jsonPath("$[0].maxDuration").value(100))
                .andExpect(jsonPath("$[0].p50Duration").exists())
                .andExpect(jsonPath("$[0].p95Duration").exists());

        mockMvc.perform(get("/api/v1/ui/spans/aggregate").param("hours", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.serviceName == 'sca-user')].spanCount").value(2))
                .andExpect(jsonPath("$[?(@.serviceName == 'pay')].spanCount").value(1));
    }

    private static TraceSpan span(String service, String spanId, long duration, boolean success) {
        TraceSpan span = new TraceSpan();
        span.setTraceId("trace-agg");
        span.setSpanId(spanId);
        span.setServiceName(service);
        span.setStartTime(System.currentTimeMillis());
        span.setDurationMs(duration);
        span.setSuccess(success);
        if (!success) {
            span.setStatusCode("ERROR");
        }
        return span;
    }
}
