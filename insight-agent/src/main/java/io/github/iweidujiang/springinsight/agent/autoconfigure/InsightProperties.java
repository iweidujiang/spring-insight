package io.github.iweidujiang.springinsight.agent.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 配置属性
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Data
@ConfigurationProperties(prefix = "spring.insight")
public class InsightProperties {

    /**
     * 是否启用 Spring Insight
     */
    private boolean enabled = true;

    /**
     * 控制台 SPA 的 URL 前缀（与打包时前端 {@code base} 一致）。默认 {@code /spring-insight}；设为空串则视为根路径（需自行提供匹配的静态资源构建）。
     */
    private String uiBasePath = "/spring-insight";

    /**
     * 服务名称（必填）
     */
    private String serviceName = "test-service"; // 默认值用于测试环境

    /**
     * 服务实例标识（可选，默认使用 host:port）
     */
    private String serviceInstance;

    /**
     * 采样率（0.0 - 1.0，1.0表示采样所有请求）
     */
    private double sampleRate = 1.0;

    /**
     * 是否启用 HTTP 请求追踪
     */
    private boolean httpTracingEnabled = true;

    /**
     * 需要排除的路径模式（不创建 HTTP Span，减少静态资源噪音）
     */
    private String[] excludePatterns = {
            "/actuator/**",
            "/health",
            "/prometheus",
            "/assets/**",
            "/vite.svg",
            "/favicon.ico",
            "/api/v1/**"
    };

    /**
     * 是否输出 Insight 诊断级日志（HTTP 每次触发、上下文强制清理等）；默认 false
     */
    private boolean diagnosticLogs = false;

    /**
     * HTTP 追踪排除路径：在配置的 {@link #excludePatterns} 基础上，若设置了 {@link #uiBasePath} 则自动追加 {@code {uiBasePath}/**}。
     */
    public String[] resolveExcludePatterns() {
        List<String> list = new ArrayList<>(Arrays.asList(excludePatterns));
        String normalized = normalizeUiBasePath();
        if (!normalized.isEmpty()) {
            list.add(normalized + "/**");
        }
        return list.toArray(String[]::new);
    }

    /**
     * 规范化的 UI 前缀，无则返回空串（非 {@code null}）。
     */
    public String normalizeUiBasePath() {
        if (uiBasePath == null) {
            return "";
        }
        String p = uiBasePath.trim();
        if (p.isEmpty()) {
            return "";
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /**
     * 验证配置是否有效
     */
    public void validate() {
        if (enabled) {
            if (serviceName == null || serviceName.trim().isEmpty()) {
                throw new IllegalArgumentException("spring.insight.service-name 必须配置");
            }
        }
    }
}
