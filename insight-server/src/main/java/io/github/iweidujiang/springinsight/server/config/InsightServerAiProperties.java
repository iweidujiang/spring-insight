package io.github.iweidujiang.springinsight.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * insight-server AI Advisor 配置（默认关闭）。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Data
@ConfigurationProperties(prefix = "spring.insight.server.ai")
public class InsightServerAiProperties {

    /**
     * 是否启用「解释 Trace」等 AI 能力
     */
    private boolean enabled = false;

    /**
     * Provider 标识；{@code openai-compatible} / {@code openai} / {@code deepseek} 均走 Chat Completions。
     * <p>
     * DeepSeek 示例：{@code base-url=https://api.deepseek.com/v1}，{@code model=deepseek-chat}。
     * </p>
     */
    private String provider = "openai-compatible";

    /**
     * OpenAI 兼容 API 根地址（须含 /v1，勿带 /chat/completions）。
     * <p>DeepSeek：{@code https://api.deepseek.com/v1}；OpenAI：{@code https://api.openai.com/v1}</p>
     */
    private String baseUrl = "https://api.openai.com/v1";

    /**
     * API Key；勿写入业务 Agent，仅 Server 配置（可用环境变量注入）
     */
    private String apiKey = "";

    /**
     * 模型名（自定义）：如 {@code gpt-4o-mini}、{@code deepseek-chat}、{@code deepseek-reasoner}
     */
    private String model = "gpt-4o-mini";

    /**
     * 调用超时（毫秒）
     */
    private int timeoutMs = 30000;

    /**
     * 送入 prompt 的最大 Span 条数（与 context 截断配合）
     */
    private int maxInputSpans = 40;

    /**
     * 生成 max_tokens 上限
     */
    private int maxTokens = 800;

    /**
     * @return 规范化 base-url（去掉末尾 /）
     */
    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String u = baseUrl.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /**
     * @return 规范化 api-key
     */
    public String normalizedApiKey() {
        return apiKey == null ? "" : apiKey.trim();
    }

    /**
     * @return 是否可发起 LLM 调用（开关开且 key、base-url 非空）
     */
    public boolean isInvokeReady() {
        return enabled
                && StringUtils.hasText(normalizedApiKey())
                && StringUtils.hasText(normalizedBaseUrl());
    }
}
