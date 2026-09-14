package io.github.iweidujiang.springinsight.collector.controller;

import io.github.iweidujiang.springinsight.collector.service.TraceSpanCollectorService;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.service.TraceContextExportService;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 提供API接口
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/15
 * └───────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ui")
public class CollectorApiController {

    private final TraceSpanPersistenceService traceSpanPersistenceService;
    private final TraceSpanCollectorService traceSpanCollectorService;
    private final InsightServerStorageProperties storageProperties;
    private final TraceContextExportService traceContextExportService;

    public CollectorApiController(TraceSpanPersistenceService traceSpanPersistenceService,
                                  TraceSpanCollectorService traceSpanCollectorService,
                                  InsightServerStorageProperties storageProperties,
                                  TraceContextExportService traceContextExportService) {
        this.traceSpanPersistenceService = traceSpanPersistenceService;
        this.traceSpanCollectorService = traceSpanCollectorService;
        this.storageProperties = storageProperties;
        this.traceContextExportService = traceContextExportService;
    }

    /**
     * 获取服务列表
     */
    @GetMapping("/services")
    public ResponseEntity<?> getServices() {
        try {
            List<String> services = traceSpanPersistenceService.getAllServiceNames();
            return ResponseEntity.ok(services);
        } catch (Exception e) {
            log.error("获取服务列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取最近链路（按 Trace ID 聚合；可选服务 / 状态 / 关键字）
     */
    @GetMapping("/traces/recent")
    public ResponseEntity<?> getRecentTraces(
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "status", defaultValue = "all") String status,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "minDurationMs", defaultValue = "0") long minDurationMs) {

        try {
            var traces = traceSpanPersistenceService.getRecentTraceSummaries(
                    hours, limit, service, status, q, minDurationMs);
            return ResponseEntity.ok(traces);
        } catch (Exception e) {
            log.error("获取最近链路失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取服务依赖关系
     */
    @GetMapping("/dependencies")
    public ResponseEntity<?> getDependencies(
            @RequestParam(value = "hours", defaultValue = "24") int hours) {

        try {
            var dependencies = traceSpanPersistenceService.getServiceDependencies(hours);
            log.debug("[UI接口] 服务依赖条数={}", dependencies.size());
            return ResponseEntity.ok(dependencies);
        } catch (Exception e) {
            log.error("获取服务依赖关系失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取服务统计
     */
    @GetMapping("/services/stats")
    public ResponseEntity<?> getServiceStats(
            @RequestParam(value = "hours", defaultValue = "0") int hours) {
        try {
            var stats = traceSpanPersistenceService.getSpanCountByService(hours);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取服务统计失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 服务延迟摘要（avg / p50 / p95 / 错误率），按 p95 降序
     */
    @GetMapping("/services/latency")
    public ResponseEntity<?> getServiceLatency(
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        try {
            var rows = traceSpanPersistenceService.getServiceLatencySummaries(hours, limit);
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.error("获取服务延迟摘要失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取错误分析（服务级，兼容仪表盘 / 通知）
     */
    @GetMapping("/errors/analysis")
    public ResponseEntity<?> getErrorAnalysis(
            @RequestParam(value = "hours", defaultValue = "24") int hours) {

        try {
            var errors = traceSpanPersistenceService.findHighErrorServices(hours);
            return ResponseEntity.ok(errors);
        } catch (Exception e) {
            log.error("获取错误分析失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 错误分析增强：按 HTTP 状态码 / 异常类聚合（含服务级摘要）。
     *
     * @param hours 时间窗口小时
     * @return breakdown JSON
     */
    @GetMapping("/errors/breakdown")
    public ResponseEntity<?> getErrorBreakdown(
            @RequestParam(value = "hours", defaultValue = "24") int hours) {
        try {
            return ResponseEntity.ok(traceSpanPersistenceService.findErrorBreakdown(hours));
        } catch (Exception e) {
            log.error("获取错误分类失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取指定服务参与的链路（按 Trace 聚合）
     */
    @GetMapping("/services/{serviceName}/traces")
    public ResponseEntity<?> getServiceTraces(
            @PathVariable("serviceName") String serviceName,
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @RequestParam(value = "status", defaultValue = "all") String status,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "minDurationMs", defaultValue = "0") long minDurationMs) {

        try {
            var traces = traceSpanPersistenceService.getRecentTraceSummaries(
                    hours, limit, serviceName, status, q, minDurationMs);
            return ResponseEntity.ok(traces);
        } catch (Exception e) {
            log.error("获取服务{}的链路失败", serviceName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取实时统计（与前端 /stats 路径对齐）
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStatsSummary() {
        return getRealtimeStatsBody();
    }

    /**
     * 获取实时统计（保留原名）
     */
    @GetMapping("/stats/realtime")
    public ResponseEntity<?> getRealtimeStats() {
        return getRealtimeStatsBody();
    }

    private ResponseEntity<?> getRealtimeStatsBody() {
        try {
            var collectorStats = traceSpanCollectorService.getStats();

            Map<String, Object> capacity = new LinkedHashMap<>();
            capacity.put("storageMode", traceSpanPersistenceService.getStorageMode());
            capacity.put("storedSpans", traceSpanPersistenceService.getStoredSpanCount());
            capacity.put("maxSpans", storageProperties.getMaxSpans());
            capacity.put("evictedSpans", traceSpanPersistenceService.getEvictedSpanCount());

            Map<String, Object> result = new HashMap<>();
            result.put("collectorStats", collectorStats);
            result.put("capacity", capacity);
            result.put("timestamp", Instant.now().toString());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[UI接口] 获取实时统计失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据TraceID获取完整的调用链
     */
    @GetMapping("/traces/{traceId}")
    public ResponseEntity<?> getTraceById(@PathVariable("traceId") String traceId) {
        try {
            var traceSpans = traceSpanPersistenceService.getTraceById(traceId);
            return ResponseEntity.ok(traceSpans);
        } catch (Exception e) {
            log.error("获取指定链路失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * AI 地基：导出单条 Trace 的脱敏结构化 Context（不调模型）。
     *
     * @param traceId Trace ID
     * @return schemaVersion=1 的 Context JSON；不存在时 404
     */
    @GetMapping("/traces/{traceId}/context")
    public ResponseEntity<?> getTraceContext(@PathVariable("traceId") String traceId) {
        try {
            Map<String, Object> context = traceContextExportService.buildContext(traceId);
            if (context == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(context);
        } catch (Exception e) {
            log.error("导出 Trace Context 失败: traceId={}", traceId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
