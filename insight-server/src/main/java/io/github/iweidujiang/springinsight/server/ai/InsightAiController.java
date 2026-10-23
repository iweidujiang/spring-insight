package io.github.iweidujiang.springinsight.server.ai;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 解读 API（默认关闭；OpenAI 兼容）。返回 schemaVersion=1 结构化结果，并保留 markdown。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ui")
@RequiredArgsConstructor
@Tag(name = "AI", description = "Trace / 告警 AI 解读（默认关）")
public class InsightAiController {

    private final InsightAiExplainService explainService;
    private final InsightAiAuditLog auditLog;

    /**
     * AI 开关与就绪状态（供详情页按钮灰显、设置页诚实展示）。
     *
     * @return enabled / invokeReady / provider / model / baseUrl / statusLabel / reason
     */
    @GetMapping("/ai/status")
    public Map<String, Object> status() {
        return explainService.status();
    }

    /**
     * 解释指定 Trace；失败降级为 {@code degraded=true}，不返回 500。
     *
     * @param traceId Trace ID
     * @return 解释结果；Trace 不存在时 404
     */
    @PostMapping("/traces/{traceId}/explain")
    public ResponseEntity<?> explain(@PathVariable("traceId") String traceId) {
        try {
            Map<String, Object> body = explainService.explain(traceId);
            if (body == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("[AI] 未预期异常: traceId={}", traceId, e);
            return ResponseEntity.ok(InsightAiExplainSchema.buildResponse(
                    "trace",
                    true,
                    "内部错误，请稍后重试或复制 Context",
                    "",
                    "",
                    null,
                    Map.of("traceId", traceId != null ? traceId : "")));
        }
    }

    /**
     * 错误分析页「一键解读」。
     *
     * @param hours 时间窗口小时，默认 24
     * @return 结构化解读
     */
    @PostMapping("/errors/explain")
    public ResponseEntity<?> explainErrors(
            @org.springframework.web.bind.annotation.RequestParam(value = "hours", defaultValue = "24") int hours) {
        try {
            return ResponseEntity.ok(explainService.explainErrors(hours));
        } catch (Exception e) {
            log.error("[AI] 错误解读未预期异常: hours={}", hours, e);
            return ResponseEntity.ok(InsightAiExplainSchema.buildResponse(
                    "errors", true, "内部错误，请稍后重试", "", "", null, Map.of("hours", hours)));
        }
    }

    /**
     * 拓扑调用链路解读。
     *
     * @param source 源服务
     * @param target 目标服务
     * @param hours  时段小时
     * @return 结构化解读
     */
    @PostMapping("/dependencies/explain")
    public ResponseEntity<?> explainDependency(
            @RequestParam("source") String source,
            @RequestParam("target") String target,
            @RequestParam(value = "hours", defaultValue = "24") int hours) {
        return ResponseEntity.ok(explainService.explainDependency(source, target, hours));
    }

    /**
     * Trace 对比 AI 解读：对齐同名操作并解释耗时差异。
     *
     * @param body {@code traceIdA} / {@code traceIdB}
     * @return 结构化对比；Trace 缺失时 404
     */
    @PostMapping("/ai/compare-traces")
    public ResponseEntity<?> compareTraces(@RequestBody Map<String, Object> body) {
        String traceIdA = body != null ? String.valueOf(body.getOrDefault("traceIdA", "")) : "";
        String traceIdB = body != null ? String.valueOf(body.getOrDefault("traceIdB", "")) : "";
        if ("null".equals(traceIdA)) {
            traceIdA = "";
        }
        if ("null".equals(traceIdB)) {
            traceIdB = "";
        }
        try {
            Map<String, Object> result = explainService.explainCompareTraces(traceIdA, traceIdB);
            if (result == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[AI] Trace 对比未预期异常: a={} b={}", traceIdA, traceIdB, e);
            return ResponseEntity.ok(InsightAiExplainSchema.buildCompareResponse(
                    true, "失败", "内部错误，请稍后重试",
                    "", "", "", List.of(), List.of(), traceIdA, traceIdB));
        }
    }

    /**
     * 上线前后变更归因：对比两窗错误率 / p95 / 请求量与新增错误类型、依赖边。
     *
     * @param body {@code hoursBefore} / {@code hoursAfter}，缺省均为 2
     * @return summary / changes / possibleCauses / suggestions
     */
    @PostMapping("/ai/change-attribution")
    public ResponseEntity<?> changeAttribution(@RequestBody(required = false) Map<String, Object> body) {
        int hoursBefore = intOrDefault(body, "hoursBefore", InsightAiChangeAttribution.DEFAULT_HOURS);
        int hoursAfter = intOrDefault(body, "hoursAfter", InsightAiChangeAttribution.DEFAULT_HOURS);
        try {
            return ResponseEntity.ok(explainService.explainChangeAttribution(hoursBefore, hoursAfter));
        } catch (Exception e) {
            log.error("[AI] 变更归因未预期异常: before={} after={}", hoursBefore, hoursAfter, e);
            return ResponseEntity.ok(InsightAiExplainSchema.buildChangeAttributionResponse(
                    true, "失败", "内部错误，请稍后重试",
                    "", "", "", List.of(), List.of(), List.of(), hoursBefore, hoursAfter));
        }
    }

    /**
     * @param body 请求体
     * @param key  字段名
     * @param def  缺省
     * @return 解析后的整数
     */
    private static int intOrDefault(Map<String, Object> body, String key, int def) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return def;
        }
        Object v = body.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 最近 AI 调用审计（含摘要）。
     *
     * @param limit 条数
     * @return 列表
     */
    @GetMapping("/ai/audit")
    public java.util.List<Map<String, Object>> audit(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return auditLog.recent(limit);
    }
}
