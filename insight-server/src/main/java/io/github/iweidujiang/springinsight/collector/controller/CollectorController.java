package io.github.iweidujiang.springinsight.collector.controller;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.collector.model.CollectorRequest;
import io.github.iweidujiang.springinsight.collector.service.TraceSpanCollectorService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 数据收集控制器
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class CollectorController {

    private final TraceSpanCollectorService traceSpanCollectorService;

    public CollectorController(TraceSpanCollectorService traceSpanCollectorService) {
        this.traceSpanCollectorService = traceSpanCollectorService;
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("[Collector控制器] 健康检查请求");

        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "spring-insight-server",
                "timestamp", Instant.now(),
                "version", "0.1.0-SNAPSHOT"
        ));
    }

    /**
     * 批量上报接口（主接口）
     */
    @PostMapping("/spans/batch")
    public ResponseEntity<?> collectSpansBatch(@Valid @RequestBody CollectorRequest request) {
        log.info("[Collector控制器] 收到批量Span上报请求: {}", request.getSummary());

        try {
            TraceSpanCollectorService.CollectorResponse response = traceSpanCollectorService.processBatchRequest(request);

            if (response.isSuccess()) {
                return ResponseEntity.accepted().body(response);
            } else {
                log.warn("[Collector控制器] 批量上报处理失败: {}, 原因: {}",
                        request.getSummary(), response.getMessage());
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("[Collector控制器] 批量上报处理异常: {}, 异常: {}",
                    request.getSummary(), e.getMessage(), e);

            TraceSpanCollectorService.CollectorResponse errorResponse =
                    TraceSpanCollectorService.CollectorResponse.error("服务器内部错误: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 单个Span上报接口（兼容接口）
     */
    @PostMapping("/spans/single")
    public ResponseEntity<?> collectSingleSpan(
            @RequestBody TraceSpan span,
            @RequestParam(value = "serviceName", required = false) String serviceName,
            @RequestParam(value = "serviceInstance", required = false) String serviceInstance) {

        log.debug("[Collector控制器] 收到单个Span上报: spanId={}", span.getSpanId());

        try {
            TraceSpanCollectorService.CollectorResponse response =
                    traceSpanCollectorService.processSingleSpan(span, serviceName, serviceInstance);

            if (response.isSuccess()) {
                return ResponseEntity.accepted().body(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("[Collector控制器] 单个Span上报处理异常: spanId={}, 异常: {}",
                    span.getSpanId(), e.getMessage(), e);

            TraceSpanCollectorService.CollectorResponse errorResponse =
                    TraceSpanCollectorService.CollectorResponse.error("服务器内部错误: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 获取收集器统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<TraceSpanCollectorService.CollectorStats> getStats() {
        log.debug("[Collector控制器] 获取统计信息");

        TraceSpanCollectorService.CollectorStats stats = traceSpanCollectorService.getStats();

        return ResponseEntity.ok(stats);
    }

    /**
     * 重置统计信息
     */
    @PostMapping("/stats/reset")
    public ResponseEntity<Map<String, Object>> resetStats() {
        log.info("[Collector控制器] 重置统计信息");

        traceSpanCollectorService.resetStats();

        return ResponseEntity.ok(Map.of(
                "message", "统计信息已重置",
                "timestamp", Instant.now()
        ));
    }

    /**
     * 服务器信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getServerInfo() {
        return ResponseEntity.ok(Map.of(
                "service", "spring-insight-server",
                "version", "0.1.0-SNAPSHOT",
                "startupTime", Instant.now(),
                "status", "running",
                "endpoints", Map.of(
                        "health", "/api/v1/health",
                        "batchSpans", "/api/v1/spans/batch",
                        "singleSpan", "/api/v1/spans/single",
                        "stats", "/api/v1/stats"
                )
        ));
    }
}
