package io.github.iweidujiang.springinsight.collector.controller;

import io.github.iweidujiang.springinsight.collector.service.TraceSpanCollectorService;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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

    public CollectorApiController(TraceSpanPersistenceService traceSpanPersistenceService,
                                  TraceSpanCollectorService traceSpanCollectorService) {
        this.traceSpanPersistenceService = traceSpanPersistenceService;
        this.traceSpanCollectorService = traceSpanCollectorService;
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
     * 获取最近链路
     */
    @GetMapping("/traces/recent")
    public ResponseEntity<?> getRecentTraces(
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {

        try {
            var traces = traceSpanPersistenceService.getRecentSpans(hours, limit);
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
    public ResponseEntity<?> getServiceStats() {
        try {
            var stats = traceSpanPersistenceService.getSpanCountByService();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取服务统计失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取错误分析
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
     * 获取指定服务的链路
     */
    @GetMapping("/services/{serviceName}/traces")
    public ResponseEntity<?> getServiceTraces(
            @PathVariable("serviceName") String serviceName,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {

        try {
            var traces = traceSpanPersistenceService.getRecentSpansByService(serviceName, limit);
            return ResponseEntity.ok(traces);
        } catch (Exception e) {
            log.error("获取服务{}的链路失败", serviceName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取实时统计
     */
    @GetMapping("/stats/realtime")
    public ResponseEntity<?> getRealtimeStats() {
        try {
            var collectorStats = traceSpanCollectorService.getStats();

            Map<String, Object> result = new HashMap<>();
            result.put("collectorStats", collectorStats);
            result.put("timestamp", Instant.now().toString());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取实时统计失败", e);
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
}
