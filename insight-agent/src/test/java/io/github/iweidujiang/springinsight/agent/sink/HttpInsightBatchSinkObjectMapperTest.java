package io.github.iweidujiang.springinsight.agent.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceBatchReport;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上报 ObjectMapper 须能序列化 {@link Instant}（JavaTimeModule）。
 *
 * @since 2026-09-20
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class HttpInsightBatchSinkObjectMapperTest {

    /**
     * 内置上报 mapper 应写出 ISO 时间字符串，而非抛 Instant 不支持。
     */
    @Test
    void reportingMapper_serializesInstant() throws Exception {
        ObjectMapper mapper = HttpInsightBatchSink.createReportingObjectMapper();
        TraceBatchReport report = new TraceBatchReport();
        report.setServiceName("demo");
        report.setReportTime(Instant.parse("2026-09-20T11:00:00Z"));
        String json = mapper.writeValueAsString(report);
        assertTrue(json.contains("2026-09-20"));
        assertFalse(json.contains("\"reportTime\":null"));
    }
}
