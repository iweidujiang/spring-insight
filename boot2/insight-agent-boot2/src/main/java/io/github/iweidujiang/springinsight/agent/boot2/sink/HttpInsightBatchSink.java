/**
 * HttpInsightBatchSink?? HttpURLConnection POST ? insight-server??? Java 8??
 *
 * @since?2026-09-07
 * @author???? ???????
 *
 * GitHub?https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceBatchReport;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.UUID;

public class HttpInsightBatchSink implements InsightBatchSink {

    private static final Logger log = LoggerFactory.getLogger(HttpInsightBatchSink.class);

    /** Server ?????? */
    private static final String SPANS_BATCH_PATH = "/api/v1/spans/batch";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final InsightBoot2Properties properties;
    private final ObjectMapper objectMapper;
    /** ???? URL?????? */
    private final String spansBatchUrl;

    /**
     * @param properties   Insight ????? server-url?
     * @param objectMapper Jackson
     */
    public HttpInsightBatchSink(InsightBoot2Properties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String base = properties.normalizeServerUrl();
        if (base.isEmpty()) {
            throw new IllegalArgumentException("spring.insight.server-url ????");
        }
        this.spansBatchUrl = base + SPANS_BATCH_PATH;
        log.info("[Boot2-HTTP] sink enabled, target={}", this.spansBatchUrl);
    }

    /**
     * ??? Span POST ? insight-server???????????
     *
     * @param spans ?? Span
     */
    @Override
    public void acceptTraceSpans(List<TraceSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return;
        }
        TraceBatchReport report = new TraceBatchReport();
        report.setServiceName(properties.getServiceName());
        report.setServiceInstance(resolveServiceInstance());
        report.setBatchId(UUID.randomUUID().toString());
        report.addAllSpans(spans);

        HttpURLConnection conn = null;
        try {
            byte[] body = objectMapper.writeValueAsBytes(report);
            conn = (HttpURLConnection) new URL(spansBatchUrl).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            OutputStream os = conn.getOutputStream();
            try {
                os.write(body);
                os.flush();
            } finally {
                os.close();
            }
            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                log.debug("[Boot2-HTTP] batch ok: size={}, status={}", spans.size(), status);
            } else {
                log.warn("[Boot2-HTTP] batch failed: size={}, status={}", spans.size(), status);
            }
        } catch (Exception e) {
            log.warn("[Boot2-HTTP] batch error: size={}, error={}", spans.size(), e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * ?????????????? localhost:server.port?
     *
     * @return ?????
     */
    private String resolveServiceInstance() {
        String si = properties.getServiceInstance();
        if (si != null && !si.trim().isEmpty()) {
            return si;
        }
        String port = System.getProperty("server.port", "8080");
        return "localhost:" + port;
    }
}
