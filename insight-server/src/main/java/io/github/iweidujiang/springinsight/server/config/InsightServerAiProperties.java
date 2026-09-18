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
     * Provider 标识；首期仅支持 {@code openai-compatible}
     */
    private String provider = "openai-compatible";

    /**
     * OpenAI 兼容 API 根地址（含 /v1）
     */
    private String baseUrl = "https://api.openai.com/v1";

    /**
     * API Key；勿写入业务 Agent，仅 Server 配置
     */
    private String apiKey = "";

    /**
     * 模型名
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
