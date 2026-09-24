package io.github.iweidujiang.springinsight.server.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
public class InsightAiController {

    private final InsightAiExplainService explainService;
    private final InsightAiAuditLog auditLog;

    /**
     * AI 开关与就绪状态（供详情页按钮灰显）。
     *
     * @return enabled / invokeReady / provider / model / baseUrl
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
     * 拓扑边解读。
     *
     * @param source 源服务
     * @param target 目标服务
     * @param hours  窗口
     * @return 结构化解读
     */
    @PostMapping("/dependencies/explain")
    public ResponseEntity<?> explainDependency(
            @org.springframework.web.bind.annotation.RequestParam("source") String source,
            @org.springframework.web.bind.annotation.RequestParam("target") String target,
            @org.springframework.web.bind.annotation.RequestParam(value = "hours", defaultValue = "24") int hours) {
        return ResponseEntity.ok(explainService.explainDependency(source, target, hours));
    }

    /**
     * 最近 AI 调用审计（含摘要）。
     *
     * @param limit 条数
     * @return 列表
     */
    @GetMapping("/ai/audit")
    public java.util.List<Map<String, Object>> audit(
            @org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "20") int limit) {
        return auditLog.recent(limit);
    }
}
