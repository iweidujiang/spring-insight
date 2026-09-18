package io.github.iweidujiang.springinsight.server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.server.config.InsightServerAiProperties;
import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettingsService;
import io.github.iweidujiang.springinsight.storage.service.TraceContextExportService;
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
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * @param settingsService      运行时设置
     * @param contextExportService Context 导出
     * @param objectMapper         JSON
     */
    public InsightAiExplainService(InsightRuntimeSettingsService settingsService,
                                   TraceContextExportService contextExportService,
                                   ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.contextExportService = contextExportService;
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
            String markdown = callChatCompletions(properties, truncated);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("degraded", false);
            ok.put("markdown", markdown + DISCLAIMER);
            ok.put("model", properties.getModel());
            ok.put("provider", properties.getProvider());
            ok.put("traceId", traceId);
            return ok;
        } catch (Exception e) {
            log.warn("[AI] 解释失败: traceId={}, error={}", traceId, e.getMessage());
            return degraded(properties,
                    "模型调用失败：" + safeMsg(e) + "。请检查「设置」中的 base-url/model/api-key。",
                    truncated);
        }
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

    private String callChatCompletions(InsightServerAiProperties properties, Map<String, Object> context)
            throws Exception {
        String url = properties.normalizedBaseUrl() + "/chat/completions";
        int timeoutMs = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 30_000;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("max_tokens", properties.getMaxTokens() > 0 ? properties.getMaxTokens() : 800);
        body.put("temperature", 0.2);

        List<Map<String, String>> messages = new ArrayList<>(2);
        messages.add(Map.of(
                "role", "system",
                "content", """
                        你是 Spring Insight 分布式链路诊断助手。根据用户提供的 Trace Context JSON（已脱敏）做简要分析。
                        要求：
                        1. 用简洁中文 Markdown：先结论，再可疑 Span / 服务，再建议排查步骤（最多 5 条）。
                        2. 只依据 JSON 中出现的字段，勿编造未出现的服务名、状态码或异常。
                        3. 不要输出原始 JSON 全文。
                        """));
        messages.add(Map.of(
                "role", "user",
                "content", "请解释以下 Trace Context：\n```json\n"
                        + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context)
                        + "\n```"));
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
        body.put("markdown", "### 未能自动解释\n\n" + message + "\n\n可使用页面上的「复制 Context」粘贴到 DeepSeek / ChatGPT 等。"
                + DISCLAIMER);
        body.put("model", properties.getModel() != null ? properties.getModel() : "");
        body.put("provider", properties.getProvider() != null ? properties.getProvider() : "");
        body.put("traceId", context.get("traceId"));
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
