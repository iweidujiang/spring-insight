package io.github.iweidujiang.springinsight.ui.controller;

import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 服务与拓扑查询控制器
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/11
 * └───────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "服务与拓扑", description = "服务发现与依赖拓扑查询接口")
public class ServiceTopologyController {

    private final TraceSpanPersistenceService traceSpanPersistenceService;

    public ServiceTopologyController(TraceSpanPersistenceService traceSpanPersistenceService) {
        this.traceSpanPersistenceService = traceSpanPersistenceService;
    }

    @GetMapping
    @Operation(summary = "获取所有服务名称", description = "返回所有已上报Span的服务名称列表")
    public ResponseEntity<List<String>> getAllServices() {
        log.debug("查询所有服务名称");
        List<String> services = traceSpanPersistenceService.getAllServiceNames();
        return ResponseEntity.ok(services);
    }

    @GetMapping("/{serviceName}/recent-spans")
    @Operation(summary = "查询服务最近Span", description = "查询指定服务最近的Span记录")
    public ResponseEntity<?> getRecentSpansByService(
            @PathVariable String serviceName,
            @Parameter(description = "返回条数，默认20", example = "20")
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("查询服务 {} 最近 {} 条Span", serviceName, limit);
        return ResponseEntity.ok(traceSpanPersistenceService.getRecentSpansByService(serviceName, limit));
    }

    @GetMapping("/dependencies")
    @Operation(summary = "获取服务依赖拓扑", description = "分析并返回服务间的调用依赖关系")
    public ResponseEntity<List<Map<String, Object>>> getServiceDependencies(
            @Parameter(description = "分析最近多少小时的数据，默认24", example = "24")
            @RequestParam(defaultValue = "24") int lastHours) {
        log.debug("查询最近 {} 小时的服务依赖拓扑", lastHours);
        List<Map<String, Object>> dependencies = traceSpanPersistenceService.getServiceDependencies(lastHours);
        return ResponseEntity.ok(dependencies);
    }

    @GetMapping("/stats/overview")
    @Operation(summary = "服务统计概览", description = "获取各服务的Span数量、错误率等概览信息")
    public ResponseEntity<List<Map<String, Object>>> getServiceStatsOverview(
            @RequestParam(defaultValue = "24") int lastHours) {
        log.debug("查询最近 {} 小时的服务统计概览", lastHours);
        List<Map<String, Object>> stats = traceSpanPersistenceService.findHighErrorServices(lastHours);
        return ResponseEntity.ok(stats);
    }
}
