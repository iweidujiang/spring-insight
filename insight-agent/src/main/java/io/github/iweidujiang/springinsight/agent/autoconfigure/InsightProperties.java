package io.github.iweidujiang.springinsight.agent.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Insight 配置属性（{@code spring.insight.*}）。
 * <p>
 * 优先级（由高到低）：{@code application.yml} / 环境变量 &gt;
 * {@code @EnableSpringInsight} 注解桥接 &gt; 代码默认值。
 * {@code service-name} 若仍为空，会在装配时回退到 {@code spring.application.name}。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/1/9
 */
@Data
@ConfigurationProperties(prefix = "spring.insight")
public class InsightProperties {

    /**
     * 是否启用 Spring Insight；默认 true（与 Agent Starter 自动配置一致）
     */
    private boolean enabled = true;

    /**
     * Insight Server 根地址。配置后走 HTTP 上报，例如 {@code http://localhost:9966}。
     */
    private String serverUrl;

    /**
     * 控制台 SPA URL 前缀（embedded 模式）；Server 模式控制台在 9966 根路径，此项可忽略。
     */
    private String uiBasePath = "/spring-insight";

    /**
     * 服务名称。可空：装配时回退 {@code spring.application.name}。
     * <p>不再默认写死 {@code test-service}，避免多服务都叫同一个名字污染拓扑。</p>
     */
    private String serviceName;

    /**
     * 服务实例标识；可空，默认 {@code localhost:{server.port}}
     */
    private String serviceInstance;

    /**
     * 采样率（0.0 - 1.0）；当前埋点侧尚未强制生效，预留配置
     */
    private double sampleRate = 1.0;

    /**
     * 是否启用 HTTP 请求追踪
     */
    private boolean httpTracingEnabled = true;

    /**
     * 是否把 Trace 上下文透传到 {@code @Async} / {@link org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor} 工作线程。
     * <p>默认 true；若与其它 TaskDecorator 冲突可关闭。</p>
     */
    private boolean contextPropagationEnabled = true;

    /**
     * 不创建 HTTP Span 的路径模式
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
     * 是否输出 Insight 诊断级日志
     */
    private boolean diagnosticLogs = false;

    /**
     * HTTP 追踪排除路径：配置项 + 自动追加的 UI 前缀。
     *
     * @return 排除 pattern 数组
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
     * 规范化 UI 前缀；未配置返回空串。
     *
     * @return 不以 {@code /} 结尾的前缀，或空串
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
     * 规范化 Server 根地址：去空白与末尾 {@code /}。
     *
     * @return 根 URL 或空串，从不返回 null
     */
    public String normalizeServerUrl() {
        if (serverUrl == null) {
            return "";
        }
        String u = serverUrl.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /**
     * @return 是否已配置远程 Insight Server
     */
    public boolean hasServerUrl() {
        return !normalizeServerUrl().isEmpty();
    }

    /**
     * 若 {@link #serviceName} 仍为空，则用 {@code spring.application.name} 填充。
     *
     * @param environment Spring Environment
     */
    public void resolveServiceNameFromEnvironment(Environment environment) {
        if (StringUtils.hasText(serviceName)) {
            return;
        }
        if (environment == null) {
            return;
        }
        String appName = environment.getProperty("spring.application.name");
        if (StringUtils.hasText(appName)) {
            this.serviceName = appName.trim();
        }
    }

    /**
     * 校验启用状态下的必填项。
     * <p>须在 {@link #resolveServiceNameFromEnvironment(Environment)} 之后调用。</p>
     */
    public void validate() {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(serviceName)) {
            throw new IllegalArgumentException(
                    "未解析到服务名：请配置 spring.application.name 或 spring.insight.service-name");
        }
    }
}
