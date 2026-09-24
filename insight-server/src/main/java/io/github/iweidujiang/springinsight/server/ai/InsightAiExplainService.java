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
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 基于 OpenAI 兼容 Chat Completions 做结构化解读（Trace / 错误聚合 / 拓扑边）。
 * <p>
 * 配置取自 {@link InsightRuntimeSettingsService#effectiveAi()}；结果为 schemaVersion=1
 *（summary / evidence / suggestions），并保留 markdown 兼容字段。
 * </p>
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@Service
public class InsightAiExplainService {

    private final InsightRuntimeSettingsService settingsService;
    private final TraceContextExportService contextExportService;
    private final TraceSpanPersistenceService persistenceService;
    private final InsightAiAuditLog auditLog;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** 告警 AI 调用时间戳（epoch ms），用于每小时限流 */
    private final ConcurrentLinkedDeque<Long> alertAiCallTimes = new ConcurrentLinkedDeque<>();

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
        Map<String, Object> nav = Map.of("traceId", traceId);

        Map<String, Object> gate = gateOrNull(properties, "trace", nav);
        if (gate != null) {
            return gate;
        }

        try {
            String raw = callChatCompletions(
                    properties,
                    """
                            你是 Spring Insight 分布式链路诊断助手。根据用户提供的 Trace Context JSON（已脱敏）做简要分析。
                            """ + InsightAiExplainSchema.JSON_OUTPUT_RULES + """
                            对本场景：优先指出失败或最慢的 Span（type=span，ref=spanId），再给处置建议。
                            """,
                    "请解释以下 Trace Context：\n```json\n"
                            + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(truncated)
                            + "\n```");
            Map<String, Object> parsed = InsightAiExplainSchema.parseStructured(raw, objectMapper);
            Map<String, Object> ok = InsightAiExplainSchema.buildResponse(
                    "trace", false, null, properties.getModel(), properties.getProvider(), parsed, nav);
            auditLog.record("trace", traceId, false, properties.getModel(),
                    String.valueOf(ok.getOrDefault("summary", "")));
            return ok;
        } catch (Exception e) {
            log.warn("[AI] 解释失败: traceId={}, error={}", traceId, e.getMessage());
            Map<String, Object> degraded = InsightAiExplainSchema.buildResponse(
                    "trace",
                    true,
                    "模型调用失败：" + safeMsg(e) + "。请检查「设置」中的 base-url/model/api-key；也可「复制 Context」。",
                    properties.getModel(),
                    properties.getProvider(),
                    null,
                    nav);
            auditLog.record("trace", traceId, true, properties.getModel(),
                    String.valueOf(degraded.getOrDefault("summary", "")));
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
        Map<String, Object> nav = Map.of("hours", hours);

        Map<String, Object> gate = gateOrNull(properties, "errors", nav);
        if (gate != null) {
            return gate;
        }

        Number total = (Number) breakdown.getOrDefault("total_error_spans", 0);
        if (total == null || total.longValue() <= 0) {
            Map<String, Object> emptyParsed = new LinkedHashMap<>();
            emptyParsed.put("structured", true);
            emptyParsed.put("summary", "所选时间范围内没有错误 Span");
            emptyParsed.put("evidence", List.of());
            emptyParsed.put("suggestions", List.of("扩大时间窗口，或确认业务侧是否上报错误 Span"));
            return InsightAiExplainSchema.buildResponse(
                    "errors", false, null, properties.getModel(), properties.getProvider(), emptyParsed, nav);
        }
        try {
            String raw = callChatCompletions(
                    properties,
                    """
                            你是 Spring Insight 错误分析助手。根据用户提供的错误聚合 JSON（按服务 / 状态码 / 异常类）做简要解读。
                            """ + InsightAiExplainSchema.JSON_OUTPUT_RULES + """
                            对本场景：evidence 优先用 type=service / status / exception，ref 取 JSON 中已有键；
                            若有 sample_trace_id 可用 type=trace。建议应可直接指导下钻。
                            """,
                    "请解读以下错误分析摘要：\n```json\n"
                            + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
                            + "\n```");
            Map<String, Object> parsed = InsightAiExplainSchema.parseStructured(raw, objectMapper);
            Map<String, Object> ok = InsightAiExplainSchema.buildResponse(
                    "errors", false, null, properties.getModel(), properties.getProvider(), parsed, nav);
            auditLog.record("errors", "hours=" + hours, false, properties.getModel(),
                    String.valueOf(ok.getOrDefault("summary", "")));
            return ok;
        } catch (Exception e) {
            log.warn("[AI] 错误解读失败: hours={}, error={}", hours, e.getMessage());
            Map<String, Object> degraded = InsightAiExplainSchema.buildResponse(
                    "errors", true, "模型调用失败：" + safeMsg(e),
                    properties.getModel(), properties.getProvider(), null, nav);
            auditLog.record("errors", "hours=" + hours, true, properties.getModel(),
                    String.valueOf(degraded.getOrDefault("summary", "")));
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

        Map<String, Object> nav = new LinkedHashMap<>();
        nav.put("source", source);
        nav.put("target", target);
        nav.put("hours", hours);

        String subject = source + "->" + target;
        Map<String, Object> gate = gateOrNull(properties, "dependency", nav);
        if (gate != null) {
            return gate;
        }
        if (edge == null) {
            return InsightAiExplainSchema.buildResponse(
                    "dependency",
                    true,
                    "窗口内没有 " + subject + " 的调用边。",
                    properties.getModel(),
                    properties.getProvider(),
                    null,
                    nav);
        }
        try {
            String raw = callChatCompletions(
                    properties,
                    """
                            你是 Spring Insight 拓扑助手。根据依赖边 JSON（调用次数、平均耗时等）说明这条边可能意味着什么。
                            """ + InsightAiExplainSchema.JSON_OUTPUT_RULES + """
                            对本场景：至少一条 evidence 使用 type=edge，ref 为「源服务->目标服务」；
                            建议侧重超时、错误率与下游容量。
                            """,
                    "请解释依赖边：\n```json\n"
                            + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
                            + "\n```");
            Map<String, Object> parsed = InsightAiExplainSchema.parseStructured(raw, objectMapper);
            Map<String, Object> ok = InsightAiExplainSchema.buildResponse(
                    "dependency", false, null, properties.getModel(), properties.getProvider(), parsed, nav);
            auditLog.record("dependency", subject, false, properties.getModel(),
                    String.valueOf(ok.getOrDefault("summary", "")));
            return ok;
        } catch (Exception e) {
            log.warn("[AI] 依赖边解读失败: {} error={}", subject, e.getMessage());
            Map<String, Object> degraded = InsightAiExplainSchema.buildResponse(
                    "dependency", true, "模型调用失败：" + safeMsg(e),
                    properties.getModel(), properties.getProvider(), null, nav);
            auditLog.record("dependency", subject, true, properties.getModel(),
                    String.valueOf(degraded.getOrDefault("summary", "")));
            return degraded;
        }
    }

    /**
     * 告警触发时的短解读：写入 Webhook/邮件的 {@code aiSummary} / {@code aiSuggestions}。
     * <p>
     * 未开 attachToAlerts、未配齐、或触达每小时上限时返回 {@code skipped=true}，不调模型。
     * </p>
     *
     * @param serviceName   告警服务
     * @param metric        指标名
     * @param value         当前指标值
     * @param threshold     阈值
     * @param windowMinutes 窗口分钟
     * @return 供告警 payload 合并的字段
     */
    public Map<String, Object> explainForAlert(String serviceName,
                                               String metric,
                                               double value,
                                               double threshold,
                                               int windowMinutes) {
        InsightServerAiProperties properties = settingsService.effectiveAi();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("aiAttached", false);
        if (!properties.isAttachToAlerts()) {
            out.put("skipped", true);
            out.put("skipReason", "attachToAlerts=false");
            return out;
        }
        if (!properties.isInvokeReady()) {
            out.put("skipped", true);
            out.put("skipReason", "ai_not_ready");
            return out;
        }
        if (!isOpenAiCompatible(properties.getProvider())) {
            out.put("skipped", true);
            out.put("skipReason", "provider_unsupported");
            return out;
        }
        int maxPerHour = properties.getAlertMaxPerHour() > 0 ? properties.getAlertMaxPerHour() : 10;
        if (!tryAcquireAlertQuota(maxPerHour)) {
            out.put("skipped", true);
            out.put("skipReason", "rate_limited");
            log.info("[AI] 告警解读达每小时上限({})，本次只推指标", maxPerHour);
            return out;
        }

        // 错误聚合按小时桶；短窗口也至少取近 1 小时摘要
        int hours = Math.max(1, (int) Math.ceil(windowMinutes / 60.0));
        Map<String, Object> breakdown = persistenceService.findErrorBreakdown(hours);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serviceName", serviceName);
        payload.put("metric", metric);
        payload.put("value", value);
        payload.put("threshold", threshold);
        payload.put("windowMinutes", windowMinutes);
        payload.put("totalErrorSpans", breakdown.get("total_error_spans"));
        payload.put("byService", filterServiceRows(breakdown.get("by_service"), serviceName, 5));
        payload.put("byStatusCode", limitList(breakdown.get("by_status_code"), 5));
        payload.put("byException", limitList(breakdown.get("by_exception"), 5));

        try {
            String raw = callChatCompletions(
                    properties,
                    """
                            你是 Spring Insight 告警助手。根据告警指标与错误聚合 JSON，用极短中文说明为何触发、先查什么。
                            """ + InsightAiExplainSchema.JSON_OUTPUT_RULES + """
                            对本场景：summary 不超过 60 字；suggestions 最多 3 条；evidence 可空或 1～2 条 type=service/status/exception。
                            """,
                    "请解读以下告警上下文：\n```json\n"
                            + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
                            + "\n```");
            Map<String, Object> parsed = InsightAiExplainSchema.parseStructured(raw, objectMapper);
            out.put("aiAttached", true);
            out.put("aiDegraded", false);
            out.put("aiSummary", String.valueOf(parsed.getOrDefault("summary", "")));
            @SuppressWarnings("unchecked")
            List<String> suggestions = (List<String>) parsed.getOrDefault("suggestions", List.of());
            out.put("aiSuggestions", suggestions);
            out.put("aiModel", properties.getModel() != null ? properties.getModel() : "");
            auditLog.record("alert", serviceName, false, properties.getModel(),
                    String.valueOf(out.get("aiSummary")));
            return out;
        } catch (Exception e) {
            log.warn("[AI] 告警解读失败: service={}, error={}", serviceName, e.getMessage());
            out.put("aiAttached", true);
            out.put("aiDegraded", true);
            out.put("aiSummary", "模型调用失败：" + safeMsg(e));
            out.put("aiSuggestions", List.of());
            out.put("aiModel", properties.getModel() != null ? properties.getModel() : "");
            auditLog.record("alert", serviceName, true, properties.getModel(),
                    String.valueOf(out.get("aiSummary")));
            return out;
        }
    }

    /**
     * 尝试占用一次告警 AI 配额（滑动 1 小时窗口）。
     *
     * @param maxPerHour 上限
     * @return 是否允许本次调用
     */
    boolean tryAcquireAlertQuota(int maxPerHour) {
        long now = System.currentTimeMillis();
        long cutoff = now - 3_600_000L;
        while (true) {
            Long oldest = alertAiCallTimes.peekFirst();
            if (oldest == null || oldest >= cutoff) {
                break;
            }
            alertAiCallTimes.pollFirst();
        }
        if (alertAiCallTimes.size() >= maxPerHour) {
            return false;
        }
        alertAiCallTimes.addLast(now);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> filterServiceRows(Object raw, String serviceName, int max) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            Object name = row.get("service_name");
            if (name == null) {
                name = row.get("serviceName");
            }
            if (serviceName != null && serviceName.equals(String.valueOf(name))) {
                matched.add((Map<String, Object>) row);
            }
        }
        if (matched.isEmpty()) {
            Object limited = limitList(raw, max);
            if (limited instanceof List<?> l) {
                List<Map<String, Object>> copy = new ArrayList<>();
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m) {
                        copy.add((Map<String, Object>) m);
                    }
                }
                return copy;
            }
            return List.of();
        }
        return matched.size() <= max ? matched : matched.subList(0, max);
    }

    /**
     * 未启用 / 未配齐 / provider 不支持时返回降级响应，否则 null。
     *
     * @param properties AI 配置
     * @param kind       场景
     * @param nav        导航上下文
     * @return 降级体或 null
     */
    private Map<String, Object> gateOrNull(InsightServerAiProperties properties,
                                           String kind,
                                           Map<String, Object> nav) {
        if (!properties.isEnabled()) {
            return InsightAiExplainSchema.buildResponse(
                    kind, true,
                    "AI 未启用。请到控制台「设置」打开 AI，或「复制 Context」粘贴到任意 LLM。",
                    properties.getModel(), properties.getProvider(), null, nav);
        }
        if (!properties.isInvokeReady()) {
            return InsightAiExplainSchema.buildResponse(
                    kind, true,
                    "AI 已启用但未配齐 base-url / api-key。请到「设置」页填写后重试。",
                    properties.getModel(), properties.getProvider(), null, nav);
        }
        if (!isOpenAiCompatible(properties.getProvider())) {
            return InsightAiExplainSchema.buildResponse(
                    kind, true,
                    "当前仅支持 provider=openai-compatible（可用 DeepSeek / OpenAI / 兼容网关）。",
                    properties.getModel(), properties.getProvider(), null, nav);
        }
        return null;
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
        // JSON 结构化输出略长于纯散文
        int maxTokens = properties.getMaxTokens() > 0 ? properties.getMaxTokens() : 800;
        body.put("max_tokens", Math.max(maxTokens, 600));
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
