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
 * Trace AI 解释 API（默认关闭；OpenAI 兼容，含 DeepSeek）。
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
            // 仍降级，避免打爆 UI
            return ResponseEntity.ok(Map.of(
                    "degraded", true,
                    "message", "内部错误，请稍后重试或复制 Context",
                    "markdown", "### 未能自动解释\n\n内部错误。请「复制 Context」手动解释。\n\n---\n*AI 建议，请以 Span 为准。*",
                    "traceId", traceId != null ? traceId : ""
            ));
        }
    }

    /**
     * 错误分析页「一键解读」。
     *
     * @param hours 时间窗口小时，默认 24
     * @return Markdown 解读
     */
    @PostMapping("/errors/explain")
    public ResponseEntity<?> explainErrors(
            @org.springframework.web.bind.annotation.RequestParam(value = "hours", defaultValue = "24") int hours) {
        try {
            return ResponseEntity.ok(explainService.explainErrors(hours));
        } catch (Exception e) {
            log.error("[AI] 错误解读未预期异常: hours={}", hours, e);
            return ResponseEntity.ok(Map.of(
                    "degraded", true,
                    "message", "内部错误",
                    "markdown", "### 未能自动解读\n\n内部错误，请稍后重试。\n\n---\n*AI 建议，请以 Span 为准。*",
                    "hours", hours
            ));
        }
    }

    /**
     * 拓扑边解读。
     *
     * @param source 源服务
     * @param target 目标服务
     * @param hours  窗口
     * @return Markdown
     */
    @PostMapping("/dependencies/explain")
    public ResponseEntity<?> explainDependency(
            @org.springframework.web.bind.annotation.RequestParam("source") String source,
            @org.springframework.web.bind.annotation.RequestParam("target") String target,
            @org.springframework.web.bind.annotation.RequestParam(value = "hours", defaultValue = "24") int hours) {
        return ResponseEntity.ok(explainService.explainDependency(source, target, hours));
    }

    /**
     * 最近 AI 调用审计。
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
