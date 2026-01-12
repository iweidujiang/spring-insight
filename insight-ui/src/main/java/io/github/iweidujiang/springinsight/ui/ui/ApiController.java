package io.github.iweidujiang.springinsight.ui.ui;

import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 API 控制器 - 提供 JSON 数据接口
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │ 📅 @since 2026/1/12
 * └───────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class ApiController {

    private final TraceSpanPersistenceService traceSpanPersistenceService;

    public ApiController(TraceSpanPersistenceService traceSpanPersistenceService) {
        this.traceSpanPersistenceService = traceSpanPersistenceService;
    }

    /**
     * 获取系统状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", Instant.now());
        result.put("service", "spring-insight-ui");
        result.put("version", "0.1.0");

        try {
            List<String> services = traceSpanPersistenceService.getAllServiceNames();
            result.put("monitoredServices", services.size());
            result.put("serviceNames", services);
        } catch (Exception e) {
            log.error("获取服务列表失败", e);
            result.put("monitoredServices", 0);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 获取服务列表
     */
    @GetMapping("/services")
    public ResponseEntity<List<String>> getServices() {
        try {
            List<String> services = traceSpanPersistenceService.getAllServiceNames();
            return ResponseEntity.ok(services);
        } catch (Exception e) {
            log.error("获取服务列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取服务依赖关系
     */
    @GetMapping("/dependencies")
    public ResponseEntity<List<Map<String, Object>>> getDependencies(
            @RequestParam(value = "hours", defaultValue = "24") int hours) {

        try {
            List<Map<String, Object>> dependencies = traceSpanPersistenceService.getServiceDependencies(hours);
            return ResponseEntity.ok(dependencies);
        } catch (Exception e) {
            log.error("获取服务依赖关系失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取服务统计信息
     */
    @GetMapping("/service-stats")
    public ResponseEntity<List<Map<String, Object>>> getServiceStats() {
        try {
            List<Map<String, Object>> stats = traceSpanPersistenceService.getSpanCountByService();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取服务统计信息失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取链路追踪列表
     */
    @GetMapping("/traces")
    public ResponseEntity<Map<String, Object>> getTraces(
            @RequestParam(value = "service", required = false) String serviceName,
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {

        try {
            Map<String, Object> result = new HashMap<>();

            List<?> traces;
            if (serviceName != null && !serviceName.isEmpty()) {
                traces = traceSpanPersistenceService.getRecentSpansByService(serviceName, limit);
            } else {
                traces = traceSpanPersistenceService.getRecentSpans(hours, limit);
            }

            result.put("traces", traces);
            result.put("count", traces.size());
            result.put("timestamp", Instant.now());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取链路追踪列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取单个链路详情
     */
    @GetMapping("/traces/{traceId}")
    public ResponseEntity<Map<String, Object>> getTraceDetail(@PathVariable String traceId) {
        try {
            var traceSpans = traceSpanPersistenceService.getTraceById(traceId);

            Map<String, Object> result = new HashMap<>();
            result.put("traceId", traceId);
            result.put("spans", traceSpans);
            result.put("spanCount", traceSpans.size());

            if (!traceSpans.isEmpty()) {
                // 计算统计信息
                long totalDuration = traceSpans.stream()
                        .filter(span -> span.getDurationMs() != null)
                        .mapToLong(span -> span.getDurationMs())
                        .sum();
                result.put("totalDuration", totalDuration);

                // 查找耗时最长的Span
                var slowestSpan = traceSpans.stream()
                        .filter(span -> span.getDurationMs() != null)
                        .max((a, b) -> Long.compare(a.getDurationMs(), b.getDurationMs()));
                slowestSpan.ifPresent(span -> {
                    result.put("slowestSpanId", span.getSpanId());
                    result.put("slowestOperation", span.getOperationName());
                    result.put("slowestDuration", span.getDurationMs());
                });
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取链路详情失败: {}", traceId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取错误分析数据
     */
    @GetMapping("/errors")
    public ResponseEntity<List<Map<String, Object>>> getErrorAnalysis(
            @RequestParam(value = "hours", defaultValue = "24") int hours) {

        try {
            var errorServices = traceSpanPersistenceService.findHighErrorServices(hours);
            return ResponseEntity.ok(errorServices);
        } catch (Exception e) {
            log.error("获取错误分析数据失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 实时统计信息
     */
    @GetMapping("/stats/realtime")
    public ResponseEntity<Map<String, Object>> getRealtimeStats() {
        try {
            Map<String, Object> result = new HashMap<>();

            // 获取最近1小时的数据
            var recentSpans = traceSpanPersistenceService.getRecentSpans(1, 1000);
            var services = traceSpanPersistenceService.getAllServiceNames();

            result.put("totalSpansLastHour", recentSpans.size());
            result.put("activeServices", services.size());
            result.put("timestamp", Instant.now());

            // 计算错误率
            long errorCount = recentSpans.stream()
                    .filter(span -> "ERROR".equals(span.getStatusCode()))
                    .count();
            double errorRate = recentSpans.isEmpty() ? 0 : (double) errorCount / recentSpans.size() * 100;
            result.put("errorRate", String.format("%.2f%%", errorRate));

            // 平均响应时间
            double avgDuration = recentSpans.stream()
                    .filter(span -> span.getDurationMs() != null)
                    .mapToLong(span -> span.getDurationMs())
                    .average()
                    .orElse(0.0);
            result.put("avgResponseTime", String.format("%.2fms", avgDuration));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取实时统计信息失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
