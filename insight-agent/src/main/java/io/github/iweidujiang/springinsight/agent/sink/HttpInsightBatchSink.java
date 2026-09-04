package io.github.iweidujiang.springinsight.agent.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.model.JvmMetric;
import io.github.iweidujiang.springinsight.agent.model.TraceBatchReport;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 将批量 TraceSpan 通过 HTTP POST 上报到独立 Insight Server。
 * <p>
 * 对应服务端接口：{@code POST {serverUrl}/api/v1/spans/batch}，请求体与 {@link TraceBatchReport} /
 * Collector 侧 {@code CollectorRequest} 字段对齐（serviceName、serviceInstance、batchId、spans）。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/8/13
 */
@Slf4j
public class HttpInsightBatchSink implements InsightBatchSink {

    /** 相对 Server 根地址的 Span 批量上报路径 */
    private static final String SPANS_BATCH_PATH = "/api/v1/spans/batch";

    /** 单次 HTTP 连接/请求超时，避免拖垮业务异步上报线程 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Insight 配置：提供 {@code serverUrl}、{@code serviceName}、{@code serviceInstance}
     */
    private final InsightProperties properties;

    /**
     * JSON 序列化器：将 {@link TraceBatchReport} 转为请求体
     */
    private final ObjectMapper objectMapper;

    /**
     * JDK 标准 HTTP 客户端：不依赖 spring-web，便于 WebFlux/Gateway 场景使用
     */
    private final HttpClient httpClient;

    /**
     * 完整上报 URL，构造时由 {@code serverUrl + SPANS_BATCH_PATH} 拼出并缓存
     */
    private final String spansBatchUrl;

    /**
     * @param properties   Insight 配置（须已配置非空 {@code server-url}）
     * @param objectMapper Spring Boot 自动配置的 Jackson ObjectMapper
     */
    public HttpInsightBatchSink(InsightProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        // 规范化后的根地址，例如 http://localhost:9966
        String base = properties.normalizeServerUrl();
        if (base.isEmpty()) {
            throw new IllegalArgumentException("spring.insight.server-url 不能为空");
        }
        this.spansBatchUrl = base + SPANS_BATCH_PATH;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        log.info("[HTTP上报] InsightBatchSink 已启用，目标={}", this.spansBatchUrl);
    }

    /**
     * 将一批 Span 封装为 {@link TraceBatchReport} 并 POST 到 Insight Server。
     * <p>
     * 失败只打日志，不向调用方抛出，以免中断 {@code AsyncSpanReporter} 刷盘循环。
     * </p>
     *
     * @param spans 已结束的 Span 列表；空或 {@code null} 时直接返回
     */
    @Override
    public void acceptTraceSpans(List<TraceSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return;
        }

        TraceBatchReport report = new TraceBatchReport();
        // 批次归属服务：优先用配置，与 Collector 校验字段一致
        report.setServiceName(properties.getServiceName());
        report.setServiceInstance(resolveServiceInstance());
        // 随机 batchId：便于服务端日志关联；当前服务端未做去重
        report.setBatchId(UUID.randomUUID().toString());
        report.addAllSpans(spans);

        try {
            byte[] body = objectMapper.writeValueAsBytes(report);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(spansBatchUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                log.debug("[HTTP上报] Span 批次成功: size={}, status={}", spans.size(), status);
            } else {
                log.warn("[HTTP上报] Span 批次失败: size={}, status={}, body={}",
                        spans.size(), status, abbreviate(response.body()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[HTTP上报] 被中断: size={}", spans.size());
        } catch (Exception e) {
            log.warn("[HTTP上报] Span 批次异常: size={}, error={}", spans.size(), e.getMessage());
        }
    }

    /**
     * JVM 指标暂无对应服务端存储表，仅记录 debug，避免误以为已落库。
     *
     * @param metrics JVM 指标列表
     */
    @Override
    public void acceptJvmMetrics(List<JvmMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        log.debug("[HTTP上报] JVM 指标批次已接收（Server 暂无专用接口）: size={}", metrics.size());
    }

    /**
     * 解析服务实例标识：配置优先，否则 {@code localhost:{server.port}}。
     *
     * @return 非空的 serviceInstance 字符串
     */
    private String resolveServiceInstance() {
        String si = properties.getServiceInstance();
        if (si != null && !si.isBlank()) {
            return si;
        }
        String port = System.getProperty("server.port", "8080");
        if ("0".equals(port)) {
            port = "8080";
        }
        return "localhost:" + port;
    }

    /**
     * 截断过长响应体，避免日志刷屏。
     *
     * @param body 原始响应文本
     * @return 最多 200 字符的摘要
     */
    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String t = body.trim();
        return t.length() <= 200 ? t : t.substring(0, 200) + "...";
    }
}
