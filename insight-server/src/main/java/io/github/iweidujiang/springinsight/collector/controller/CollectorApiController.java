package io.github.iweidujiang.springinsight.collector.controller;

import io.github.iweidujiang.springinsight.collector.service.TraceSpanCollectorService;
import io.github.iweidujiang.springinsight.server.anomaly.AnomalyDetectionResult;
import io.github.iweidujiang.springinsight.server.anomaly.AnomalyDetector;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.server.topology.TopologyDiffService;
import io.github.iweidujiang.springinsight.storage.service.TraceContextExportService;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import io.github.iweidujiang.springinsight.storage.service.TroubleshootPackExportService;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Console", description = "控制台查询（拓扑 / Trace / 服务等）")
public class CollectorApiController {

    private final TraceSpanPersistenceService traceSpanPersistenceService;
    private final TraceSpanCollectorService traceSpanCollectorService;
    private final InsightServerStorageProperties storageProperties;
    private final TraceContextExportService traceContextExportService;
    private final TroubleshootPackExportService troubleshootPackExportService;
    private final SpanStore spanStore;
    private final AnomalyDetector anomalyDetector;
    private final TopologyDiffService topologyDiffService;

    public CollectorApiController(TraceSpanPersistenceService traceSpanPersistenceService,
                                  TraceSpanCollectorService traceSpanCollectorService,
                                  InsightServerStorageProperties storageProperties,
                                  TraceContextExportService traceContextExportService,
                                  TroubleshootPackExportService troubleshootPackExportService,
                                  SpanStore spanStore,
                                  AnomalyDetector anomalyDetector,
                                  TopologyDiffService topologyDiffService) {
        this.traceSpanPersistenceService = traceSpanPersistenceService;
        this.traceSpanCollectorService = traceSpanCollectorService;
        this.storageProperties = storageProperties;
        this.traceContextExportService = traceContextExportService;
        this.troubleshootPackExportService = troubleshootPackExportService;
        this.spanStore = spanStore;
        this.anomalyDetector = anomalyDetector;
        this.topologyDiffService = topologyDiffService;
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
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "status", defaultValue = "all") String status,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "minDurationMs", defaultValue = "0") long minDurationMs,
            @RequestParam(value = "pathPrefix", required = false) String pathPrefix) {

        try {
            if (offset == null) {
                int max = limit == null ? 100 : limit;
                var traces = traceSpanPersistenceService.getRecentTraceSummaries(
                        hours, max, service, status, q, minDurationMs, pathPrefix);
                return ResponseEntity.ok(traces);
            }
            int pageSize = limit == null ? 50 : Math.min(200, Math.max(1, limit));
            int off = Math.max(0, offset);
            long since = hours <= 0 ? 0L : Instant.now().minus(hours, ChronoUnit.HOURS).toEpochMilli();
            var data = spanStore.listTraces(since, service, status, q, minDurationMs, pathPrefix, off, pageSize, sort);
            long total = spanStore.countTraces(since, service, status, q, minDurationMs, pathPrefix);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("data", data);
            body.put("total", total);
            body.put("offset", off);
            body.put("limit", pageSize);
            return ResponseEntity.ok(body);
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
     * 拓扑边（与 {@code /dependencies} 相同数据，便于按拓扑语义调用）。
     *
     * @param hours 时间窗小时
     * @return 依赖边列表
     */
    @GetMapping("/topology")
    public ResponseEntity<?> getTopology(
            @RequestParam(value = "hours", defaultValue = "24") int hours) {
        return getDependencies(hours);
    }

    /**
     * 两时段拓扑差异：新增边 / 消失边 / 调用量变化超过 50% 的边。
     *
     * @param hoursA 基线窗口小时
     * @param hoursB 对比窗口小时
     * @return added / removed / changed
     */
    @GetMapping("/topology/diff")
    public ResponseEntity<?> getTopologyDiff(
            @RequestParam(value = "hoursA", defaultValue = "24") int hoursA,
            @RequestParam(value = "hoursB", defaultValue = "1") int hoursB) {
        try {
            return ResponseEntity.ok(topologyDiffService.diff(hoursA, hoursB));
        } catch (Exception e) {
            log.error("拓扑对比失败: hoursA={} hoursB={}", hoursA, hoursB, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "拓扑对比失败"));
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
     * 服务级 Span 聚合。时间窗内按服务汇总条数、错误和耗时，不把全部 Span 载入响应。
     *
     * @param hours   时间窗小时；默认 1。小于等于 0 表示不设下界
     * @param service 可选服务名；空白表示全部服务
     * @return 每个服务一行
     */
    @GetMapping("/spans/aggregate")
    public ResponseEntity<?> aggregateSpans(
            @RequestParam(value = "hours", defaultValue = "1") int hours,
            @RequestParam(value = "service", required = false) String service) {
        try {
            long since = hours <= 0 ? 0L : Instant.now().minus(hours, ChronoUnit.HOURS).toEpochMilli();
            String filter = service == null || service.isBlank() ? null : service.trim();
            return ResponseEntity.ok(spanStore.aggregateSnapshot(since, filter));
        } catch (Exception e) {
            log.error("获取服务级聚合失败", e);
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
            return ResponseEntity.ok(attachAnomalyMarks(rows));
        } catch (Exception e) {
            log.error("获取服务延迟摘要失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 把无阈值异常检测结果挂到服务行，便于 UI 在无告警规则命中时仍可标记。
     *
     * @param rows 延迟摘要
     * @return 附带 {@code anomaly} / {@code anomalyDetail} 的行
     */
    private List<Map<String, Object>> attachAnomalyMarks(List<Map<String, Object>> rows) {
        if (rows == null || anomalyDetector == null) {
            return rows;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String service = String.valueOf(row.getOrDefault("service_name", ""));
            AnomalyDetectionResult hit = anomalyDetector.latestAnomalyForService(service);
            if (hit != null && hit.isAnomaly()) {
                row.put("anomaly", true);
                row.put("anomalyDetail", hit.toMap());
            } else {
                row.put("anomaly", false);
            }
        }
        return rows;
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
     * JDBC 追踪汇总（数据库页）：KPI、按库聚合、最近 Span。
     *
     * @param hours       时间窗小时
     * @param service     可选业务服务名
     * @param recentLimit 最近 JDBC Span 条数
     * @return summary JSON
     */
    @GetMapping("/jdbc/summary")
    public ResponseEntity<?> getJdbcSummary(
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "recentLimit", defaultValue = "50") int recentLimit) {
        try {
            return ResponseEntity.ok(traceSpanPersistenceService.getJdbcSummary(hours, service, recentLimit));
        } catch (Exception e) {
            log.error("获取 JDBC 汇总失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Redis 追踪汇总（缓存页）。
     *
     * @param hours       时间窗小时
     * @param service     可选业务服务名
     * @param recentLimit 最近 REDIS Span 条数
     * @return summary JSON
     */
    @GetMapping("/redis/summary")
    public ResponseEntity<?> getRedisSummary(
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "recentLimit", defaultValue = "50") int recentLimit) {
        try {
            return ResponseEntity.ok(traceSpanPersistenceService.getRedisSummary(hours, service, recentLimit));
        } catch (Exception e) {
            log.error("获取 Redis 汇总失败", e);
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
     * 单服务详情摘要：近窗 KPI、慢操作 Top、出入站依赖边、最近 Trace。
     *
     * @param serviceName 服务名
     * @param hours       时间窗小时
     * @param recentLimit 最近 Trace 条数
     * @param slowOpLimit 慢操作 Top 条数
     * @return summary JSON
     */
    @GetMapping("/services/{serviceName}/summary")
    public ResponseEntity<?> getServiceSummary(
            @PathVariable("serviceName") String serviceName,
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            @RequestParam(value = "recentLimit", defaultValue = "15") int recentLimit,
            @RequestParam(value = "slowOpLimit", defaultValue = "10") int slowOpLimit) {
        try {
            return ResponseEntity.ok(traceSpanPersistenceService.getServiceDetailSummary(
                    serviceName, hours, recentLimit, slowOpLimit));
        } catch (Exception e) {
            log.error("获取服务{}详情摘要失败", serviceName, e);
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
            @RequestParam(value = "minDurationMs", defaultValue = "0") long minDurationMs,
            @RequestParam(value = "pathPrefix", required = false) String pathPrefix) {

        try {
            var traces = traceSpanPersistenceService.getRecentTraceSummaries(
                    hours, limit, serviceName, status, q, minDurationMs, pathPrefix);
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

    /**
     * 脱敏排障包：Trace Context + Server 版本 + 时段窗摘要（不调模型）。
     *
     * @param traceId Trace ID
     * @param hours   窗摘要小时数；{@code <=0} 表示全部已存
     * @return schemaVersion=1 排障包；不存在时 404
     */
    @GetMapping("/traces/{traceId}/troubleshoot-pack")
    public ResponseEntity<?> getTroubleshootPack(
            @PathVariable("traceId") String traceId,
            @RequestParam(value = "hours", defaultValue = "24") int hours) {
        try {
            Map<String, Object> pack = troubleshootPackExportService.buildPack(traceId, hours);
            if (pack == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(pack);
        } catch (Exception e) {
            log.error("导出排障包失败: traceId={}", traceId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
