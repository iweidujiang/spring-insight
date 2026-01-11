package io.github.iweidujiang.springinsight.ui.controller;

import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 链路追踪查询控制器
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
@RequestMapping("/api/v1/traces")
@Tag(name = "链路追踪", description = "链路追踪查询接口")
public class TraceQueryController {

    private final TraceSpanPersistenceService traceSpanPersistenceService;

    public TraceQueryController(TraceSpanPersistenceService traceSpanPersistenceService) {
        this.traceSpanPersistenceService = traceSpanPersistenceService;
    }

    @GetMapping("/{traceId}")
    @Operation(summary = "根据TraceID查询完整链路", description = "通过全局唯一的TraceID查询一次请求的完整调用链")
    public ResponseEntity<?> getTraceByTraceId(@PathVariable("traceId") String traceId) {
        log.debug("根据TraceID查询完整链路: {}", traceId);
        List<?> trace = traceSpanPersistenceService.getTraceById(traceId);
        if (trace.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(trace);
    }

    @GetMapping
    @Operation(summary = "条件查询Span", description = "根据时间范围、服务名、状态等条件查询Span")
    public ResponseEntity<?> querySpans(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String operationName,
            @RequestParam(required = false) String statusCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "100") int limit) {

        log.debug("条件查询Span: service={}, operation={}, status={}", serviceName, operationName, statusCode);
        // 这里可以构建一个复杂的查询条件对象，调用Service的查询方法
        // 为简化演示，先返回最近的数据
        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(1);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        long startMillis = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // 此处需要在 TraceSpanPersistenceService 中实现一个按时间范围查询的方法
        // 暂时返回最近数据作为示例
        return ResponseEntity.ok(traceSpanPersistenceService.getRecentSpans(1, limit));
    }

    @GetMapping("/recent")
    @Operation(summary = "查询最近Span", description = "查询系统最近记录的Span，用于调试和实时监控")
    public ResponseEntity<?> getRecentSpans(
            @RequestParam(defaultValue = "1") int lastHours,
            @RequestParam(defaultValue = "50") int limit) {
        log.debug("查询最近 {} 小时内的 {} 条Span", lastHours, limit);
        return ResponseEntity.ok(traceSpanPersistenceService.getRecentSpans(lastHours, limit));
    }
}
