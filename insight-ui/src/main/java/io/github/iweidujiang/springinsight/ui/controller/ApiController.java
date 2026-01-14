package io.github.iweidujiang.springinsight.ui.controller;

import io.github.iweidujiang.springinsight.ui.service.DataCollectorService;
import io.github.iweidujiang.springinsight.ui.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
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

    private final DataCollectorService dataCollectorService;
    private final WebSocketService webSocketService;

    public ApiController(DataCollectorService dataCollectorService, WebSocketService webSocketService) {
        this.dataCollectorService = dataCollectorService;
        this.webSocketService = webSocketService;
    }

    /**
     * 获取实时统计
     */
    @GetMapping("/realtime-stats")
    public ResponseEntity<Map<String, Object>> getRealtimeStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            stats.put("collectorStats", dataCollectorService.getCollectorStats());
            stats.put("serviceStats", dataCollectorService.getServiceStats());
            stats.put("errorAnalysis", dataCollectorService.getErrorAnalysis(1));
            stats.put("timestamp", Instant.now().toString());
            stats.put("cacheSize", dataCollectorService.getCacheSize());

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取实时统计失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 刷新数据缓存
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshData() {
        try {
            dataCollectorService.clearCache();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "数据缓存已刷新");
            response.put("timestamp", Instant.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("刷新数据失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取系统状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        try {
            Map<String, Object> status = new HashMap<>();

            status.put("status", "UP");
            status.put("service", "spring-insight-ui");
            status.put("version", "0.1.0");
            status.put("timestamp", Instant.now().toString());
            status.put("cacheSize", dataCollectorService.getCacheSize());
            status.put("websocketConnections", webSocketService.getConnectionCount());
            status.put("collectorUrl", dataCollectorService.getCollectorUrl());

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("获取系统状态失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 发送测试告警
     */
    @PostMapping("/test-alert")
    public ResponseEntity<Map<String, Object>> sendTestAlert(
            @RequestParam(defaultValue = "测试服务") String serviceName,
            @RequestParam(defaultValue = "这是一个测试告警") String message,
            @RequestParam(defaultValue = "warning") String level) {

        try {
            webSocketService.broadcastErrorAlert(serviceName, message, level);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "测试告警已发送");
            response.put("timestamp", Instant.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("发送测试告警失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取监控指标
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();

            // 系统指标
            Runtime runtime = Runtime.getRuntime();
            metrics.put("jvmMemoryUsed", runtime.totalMemory() - runtime.freeMemory());
            metrics.put("jvmMemoryMax", runtime.maxMemory());
            metrics.put("jvmMemoryTotal", runtime.totalMemory());
            metrics.put("availableProcessors", runtime.availableProcessors());

            // 应用指标
            metrics.put("cacheSize", dataCollectorService.getCacheSize());
            metrics.put("websocketConnections", webSocketService.getConnectionCount());

            // collector指标
            var collectorStats = dataCollectorService.getCollectorStats();
            metrics.put("collectorRequests", collectorStats.getTotalReceivedRequests());
            metrics.put("collectorSpans", collectorStats.getTotalReceivedSpans());
            metrics.put("collectorSuccessRate", collectorStats.getSuccessRate());

            metrics.put("timestamp", Instant.now().toString());

            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("获取监控指标失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
