package io.github.iweidujiang.springinsight.server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.server.config.InsightServerAiProperties;
import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettingsService;
import io.github.iweidujiang.springinsight.storage.service.TraceContextExportService;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 OpenAI 兼容 Chat Completions 解释 Trace（DeepSeek / OpenAI / 兼容网关等）。
 * <p>
 * 配置取自 {@link InsightRuntimeSettingsService#effectiveAi()}（控制台可改）。
 * </p>
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@Service
public class InsightAiExplainService {

    private static final String DISCLAIMER = "\n\n---\n*AI 建议，请以 Span 为准。*";

    private final InsightRuntimeSettingsService settingsService;
    private final TraceContextExportService contextExportService;
    private final TraceSpanPersistenceService persistenceService;
    private final InsightAiAuditLog auditLog;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * @param settingsService      运行时设置
     * @param contextExportService Context 导出
     * @param persistenceService   错误/依赖聚合
     * @param auditLog             调用审计
     * @param objectMapper         JSON
     */
    public InsightAiExplainService(InsightRuntimeSettingsService settingsService,
                                   TraceContextExportService contextExportService,
                                   TraceSpanPersistenceService persistenceService,
                                   InsightAiAuditLog auditLog,
                                   ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.contextExportService = contextExportService;
        this.persistenceService = persistenceService;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * @return 状态摘要（UI 灰显按钮用）
     */
    public Map<String, Object> status() {
        InsightServerAiProperties properties = settingsService.effectiveAi();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", properties.isEnabled());
        body.put("invokeReady", properties.isInvokeReady());
        body.put("provider", properties.getProvider() != null ? properties.getProvider() : "");
        body.put("model", properties.getModel() != null ? properties.getModel() : "");
        body.put("baseUrl", properties.normalizedBaseUrl());
        return body;
    }

    /**
     * 解释指定 Trace；未启用或调用失败时返回 {@code degraded=true}，不抛 500。
     *
     * @param traceId Trace ID
     * @return 响应体；Trace 不存在时返回 null
     */
    public Map<String, Object> explain(String traceId) {
        InsightServerAiProperties properties = settingsService.effectiveAi();
        Map<String, Object> context = contextExportService.buildContext(traceId);
        if (context == null) {
            return null;
        }
        Map<String, Object> truncated = truncateContext(context, properties.getMaxInputSpans());

        if (!properties.isEnabled()) {
            return degraded(properties,
                    "AI 未启用。请到控制台「设置」打开 AI，或「复制 Context」粘贴到任意 LLM。",
                    truncated);
        }
        if (!properties.isInvokeReady()) {
            return degraded(properties,
                    "AI 已启用但未配齐 base-url / api-key。请到「设置」页填写后重试。",
                    truncated);
        }
        if (!isOpenAiCompatible(properties.getProvider())) {
            return degraded(properties,
                    "当前仅支持 provider=openai-compatible（可用 DeepSeek / OpenAI / 兼容网关）。",
                    truncated);
        }

        try {
            String markdown = callChatCompletions(
                    properties,
                    """
                            你是 Spring Insight 分布式链路诊断助手。根据用户提供的 Trace Context JSON（已脱敏）做简要分析。
                            要求：
                            1. 用简洁中文 Markdown：先结论，再可疑 Span / 服务，再建议排查步骤（最多 5 条）。
                            2. 只依据 JSON 中出现的字段，勿编造未出现的服务名、状态码或异常。
                            3. 不要输出原始 JSON 全文。
                            """,
                    "请解释以下 Trace Context：\n```json\n"
                            + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(truncated)
                            + "\n```");
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("degraded", false);
            ok.put("markdown", markdown + DISCLAIMER);
            ok.put("model", properties.getModel());
            ok.put("provider", properties.getProvider());
            ok.put("traceId", traceId);
            auditLog.record("trace", traceId, false, properties.getModel());
            return ok;
        } catch (Exception e) {
            log.warn("[AI] 解释失败: traceId={}, error={}", traceId, e.getMessage());
            Map<String, Object> degraded = degraded(properties,
                    "模型调用失败：" + safeMsg(e) + "。请检查「设置」中的 base-url/model/api-key。",
                    truncated);
            auditLog.record("trace", traceId, true, properties.getModel());
            return degraded;
        }
    }

    /**
     * 解释错误分析聚合摘要（错误页「一键解读」）。
     *
     * @param hours 时间窗口小时；{@code <=0} 表示全部已存
     * @return 解释结果
     */
    public Map<String, Object> explainErrors(int hours) {
        InsightServerAiProperties properties = settingsService.effectiveAi();
        Map<String, Object> breakdown = persistenceService.findErrorBreakdown(hours);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hours", hours);
        payload.put("totalErrorSpans", breakdown.get("total_error_spans"));
        payload.put("byService", limitList(breakdown.get("by_service"), 15));
        payload.put("byStatusCode", limitList(breakdown.get("by_status_code"), 10));
        payload.put("byException", limitList(breakdown.get("by_exception"), 10));

        if (!properties.isEnabled()) {
            return degradedErrors(properties, "AI 未启用。请到控制台「设置」打开 AI。", payload);
        }
        if (!properties.isInvokeReady()) {
            return degradedErrors(properties, "AI 已启用但未配齐 base-url / api-key。", payload);
        }
        if (!isOpenAiCompatible(properties.getProvider())) {
            return degradedErrors(properties, "当前仅支持 OpenAI 兼容 Chat Completions。", payload);
        }
        Number total = (Number) breakdown.getOrDefault("total_error_spans", 0);
        if (total == null || total.longValue() <= 0) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("degraded", false);
            empty.put("markdown", "### 暂无错误可解读\n\n所选时间范围内没有错误 Span。" + DISCLAIMER);
            empty.put("model", properties.getModel());
            empty.put("provider", properties.getProvider());
            empty.put("hours", hours);
            return empty;
        }
        try {
            String markdown = callChatCompletions(
                    properties,
                    """
                            你是 Spring Insight 错误分析助手。根据用户提供的错误聚合 JSON（按服务 / 状态码 / 异常类）做简要解读。
                            要求：
                            1. 用简洁中文 Markdown：先总览，再最值得先查的 2～3 个线索，再建议排查步骤（最多 5 条）。
                            2. 只依据 JSON，勿编造未出现的服务名或状态码。
                            3. 不要输出原始 JSON 全文。
                            """,
                    "请解读以下错误分析摘要：\n```json\n"
                            + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
                            + "\n```");
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("degraded", false);
            ok.put("markdown", markdown + DISCLAIMER);
            ok.put("model", properties.getModel());
            ok.put("provider", properties.getProvider());
            ok.put("hours", hours);
            auditLog.record("errors", "hours=" + hours, false, properties.getModel());
            return ok;
        } catch (Exception e) {
            log.warn("[AI] 错误解读失败: hours={}, error={}", hours, e.getMessage());
            Map<String, Object> degraded = degradedErrors(properties, "模型调用失败：" + safeMsg(e), payload);
            auditLog.record("errors", "hours=" + hours, true, properties.getModel());
            return degraded;
        }
    }

    /**
     * 解释拓扑边（源 → 目标）调用摘要。
     *
     * @param source 源服务
     * @param target 目标服务
     * @param hours  窗口小时
     * @return 解读结果
     */
    public Map<String, Object> explainDependency(String source, String target, int hours) {
        InsightServerAiProperties properties = settingsService.effectiveAi();
        List<Map<String, Object>> deps = persistenceService.getServiceDependencies(hours > 0 ? hours : 24);
        Map<String, Object> edge = null;
        for (Map<String, Object> row : deps) {
            String s = String.valueOf(row.getOrDefault("source_service", row.get("sourceService")));
            String t = String.valueOf(row.getOrDefault("target_service", row.get("targetService")));
            if (source.equals(s) && target.equals(t)) {
                edge = row;
                break;
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", source);
        payload.put("target", target);
        payload.put("hours", hours);
        payload.put("edge", edge);

        String subject = source + "->" + target;
        if (!properties.isEnabled()) {
            return degradedEdge(properties, "AI 未启用。", payload);
        }
        if (!properties.isInvokeReady()) {
            return degradedEdge(properties, "AI 未配齐 base-url / api-key。", payload);
        }
        if (edge == null) {
            Map<String, Object> miss = new LinkedHashMap<>();
            miss.put("degraded", true);
            miss.put("message", "未找到该依赖边");
            miss.put("markdown", "### 未找到依赖\n\n窗口内没有 " + subject + " 的调用边。" + DISCLAIMER);
            miss.put("source", source);
            miss.put("target", target);
            return miss;
        }
        try {
            String markdown = callChatCompletions(
                    properties,
                    """
                            你是 Spring Insight 拓扑助手。根据依赖边 JSON（调用次数、平均耗时等）说明这条边可能意味着什么。
                            要求：简洁中文 Markdown；勿编造未出现的指标；最多 4 条建议。
                            """,
                    "请解释依赖边：\n```json\n"
                            + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
                            + "\n```");
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("degraded", false);
            ok.put("markdown", markdown + DISCLAIMER);
            ok.put("model", properties.getModel());
            ok.put("provider", properties.getProvider());
            ok.put("source", source);
            ok.put("target", target);
            auditLog.record("dependency", subject, false, properties.getModel());
            return ok;
        } catch (Exception e) {
            log.warn("[AI] 依赖边解读失败: {} error={}", subject, e.getMessage());
            Map<String, Object> degraded = degradedEdge(properties, "模型调用失败：" + safeMsg(e), payload);
            auditLog.record("dependency", subject, true, properties.getModel());
            return degraded;
        }
    }

    private Map<String, Object> degradedEdge(InsightServerAiProperties properties,
                                             String message,
                                             Map<String, Object> payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("degraded", true);
        body.put("message", message);
        body.put("markdown", "### 未能解释该边\n\n" + message + DISCLAIMER);
        body.put("model", properties.getModel() != null ? properties.getModel() : "");
        body.put("provider", properties.getProvider() != null ? properties.getProvider() : "");
        body.put("source", payload.get("source"));
        body.put("target", payload.get("target"));
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Object limitList(Object raw, int max) {
        if (!(raw instanceof List<?> list) || list.size() <= max) {
            return raw;
        }
        return list.subList(0, max);
    }

    /**
     * @param context       原始 Context
     * @param maxInputSpans 最大 Span 条数
     * @return 可能截断后的副本
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> truncateContext(Map<String, Object> context, int maxInputSpans) {
        Map<String, Object> copy = new LinkedHashMap<>(context);
        Object spansObj = copy.get("spans");
        if (!(spansObj instanceof List<?> spans) || spans.isEmpty()) {
            return copy;
        }
        int max = maxInputSpans > 0 ? maxInputSpans : 40;
        if (spans.size() <= max) {
            return copy;
        }
        List<Map<String, Object>> keep = new ArrayList<>(max);
        for (Object o : spans) {
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            Object success = row.get("success");
            if (Boolean.FALSE.equals(success) && keep.size() < max) {
                keep.add((Map<String, Object>) row);
            }
        }
        for (Object o : spans) {
            if (keep.size() >= max) {
                break;
            }
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            if (!keep.contains(row)) {
                keep.add((Map<String, Object>) row);
            }
        }
        copy.put("spans", keep);
        copy.put("truncated", true);
        copy.put("originalSpanCount", spans.size());
        return copy;
    }

    private String callChatCompletions(InsightServerAiProperties properties,
                                       String systemPrompt,
                                       String userContent) throws Exception {
        String url = properties.normalizedBaseUrl() + "/chat/completions";
        int timeoutMs = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 30_000;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("max_tokens", properties.getMaxTokens() > 0 ? properties.getMaxTokens() : 800);
        body.put("temperature", 0.2);

        List<Map<String, String>> messages = new ArrayList<>(2);
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userContent));
        body.put("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.normalizedApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + ": " + abbreviate(response.body(), 200));
        }
        return extractContent(response.body());
    }

    /**
     * @param responseBody Chat Completions JSON
     * @return assistant 文本
     */
    String extractContent(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || !content.isTextual() || content.asText().isBlank()) {
            throw new IllegalStateException("响应缺少 choices[0].message.content");
        }
        return content.asText().trim();
    }

    private Map<String, Object> degraded(InsightServerAiProperties properties,
                                         String message,
                                         Map<String, Object> context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("degraded", true);
        body.put("message", message);
        body.put("markdown", "### 未能自动解释\n\n" + message + "\n\n可使用页面上的「复制 Context」粘贴到任意 LLM。"
                + DISCLAIMER);
        body.put("model", properties.getModel() != null ? properties.getModel() : "");
        body.put("provider", properties.getProvider() != null ? properties.getProvider() : "");
        body.put("traceId", context.get("traceId"));
        return body;
    }

    private Map<String, Object> degradedErrors(InsightServerAiProperties properties,
                                               String message,
                                               Map<String, Object> payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("degraded", true);
        body.put("message", message);
        body.put("markdown", "### 未能自动解读\n\n" + message + DISCLAIMER);
        body.put("model", properties.getModel() != null ? properties.getModel() : "");
        body.put("provider", properties.getProvider() != null ? properties.getProvider() : "");
        body.put("hours", payload.get("hours"));
        return body;
    }

    private static boolean isOpenAiCompatible(String provider) {
        if (!StringUtils.hasText(provider)) {
            return true;
        }
        String p = provider.trim().toLowerCase();
        return "openai-compatible".equals(p) || "openai".equals(p) || "deepseek".equals(p);
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m != null && !m.isBlank() ? abbreviate(m, 160) : e.getClass().getSimpleName();
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
