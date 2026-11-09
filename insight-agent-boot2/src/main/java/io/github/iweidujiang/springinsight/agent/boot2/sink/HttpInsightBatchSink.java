package io.github.iweidujiang.springinsight.agent.boot2.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.model.JvmMetric;
import io.github.iweidujiang.springinsight.agent.boot2.model.JvmMetricBatchReport;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceBatchReport;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.UUID;

/**
 * HttpInsightBatchSink：用 HttpURLConnection POST 上报到 insight-server（兼容 Java 8）。
 *
 * @since 2026-09-07
 * @author 苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
public class HttpInsightBatchSink implements InsightBatchSink {

    private static final Logger log = LoggerFactory.getLogger(HttpInsightBatchSink.class);

    /** Server 相对路径：Span 批量上报 */
    private static final String SPANS_BATCH_PATH = "/api/v1/spans/batch";
    /** Server 相对路径：JVM 指标批量上报 */
    private static final String JVM_METRICS_BATCH_PATH = "/api/v1/jvm-metrics/batch";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final InsightBoot2Properties properties;
    private final ObjectMapper objectMapper;
    /** Span 完整上报 URL（构造时缓存） */
    private final String spansBatchUrl;
    /** JVM 指标完整上报 URL */
    private final String jvmMetricsBatchUrl;

    /**
     * @param properties   Insight 配置（须含 server-url）
     * @param objectMapper Jackson
     */
    public HttpInsightBatchSink(InsightBoot2Properties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String base = properties.normalizeServerUrl();
        if (base.isEmpty()) {
            throw new IllegalArgumentException("spring.insight.server-url 不能为空");
        }
        this.spansBatchUrl = base + SPANS_BATCH_PATH;
        this.jvmMetricsBatchUrl = base + JVM_METRICS_BATCH_PATH;
        log.info("[HTTP上报-Boot2] 已启用，目标={} / {}", this.spansBatchUrl, this.jvmMetricsBatchUrl);
    }

    /**
     * 将批量 Span POST 到 insight-server；失败仅打日志不抛异常。
     *
     * @param spans 待上报 Span
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

        postJson(spansBatchUrl, report, spans.size(), "Span");
    }

    /**
     * 将批量 JVM 指标 POST 到 insight-server；失败仅打日志不抛异常。
     *
     * @param metrics 待上报指标
     */
    @Override
    public void acceptJvmMetrics(List<JvmMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        JvmMetricBatchReport report = new JvmMetricBatchReport();
        report.setServiceName(properties.getServiceName());
        report.setServiceInstance(resolveServiceInstance());
        report.setBatchId(UUID.randomUUID().toString());
        report.addAllMetrics(metrics);
        postJson(jvmMetricsBatchUrl, report, metrics.size(), "JVM");
    }

    /**
     * 统一 POST JSON。
     *
     * @param url       目标 URL
     * @param bodyObj   请求体对象
     * @param size      条数（日志）
     * @param kindLabel 类型标签
     */
    private void postJson(String url, Object bodyObj, int size, String kindLabel) {
        HttpURLConnection conn = null;
        try {
            byte[] body = objectMapper.writeValueAsBytes(bodyObj);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            String token = properties.normalizedIngestToken();
            if (!token.isEmpty()) {
                conn.setRequestProperty("X-Insight-Token", token);
            }
            OutputStream os = conn.getOutputStream();
            try {
                os.write(body);
                os.flush();
            } finally {
                os.close();
            }
            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                log.debug("[HTTP上报-Boot2] {} 批次成功: size={}, status={}", kindLabel, Integer.valueOf(size), Integer.valueOf(status));
            } else {
                log.warn("[HTTP上报-Boot2] {} 批次失败: size={}, status={}", kindLabel, Integer.valueOf(size), Integer.valueOf(status));
            }
        } catch (Exception e) {
            log.warn("[HTTP上报-Boot2] {} 批次异常: size={}, error={}", kindLabel, Integer.valueOf(size), e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 解析服务实例标识；未配置时回退 localhost:server.port。
     *
     * @return 实例标识串
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
