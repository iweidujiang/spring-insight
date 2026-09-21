package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * InsightBoot2Properties：Boot2 线 spring.insight.* 配置。
 *
 * @since 2026-09-07
 * @author 苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
@ConfigurationProperties(prefix = "spring.insight")
public class InsightBoot2Properties {

    private boolean enabled = true;
    private String serverUrl;
    private String serviceName;
    private String serviceInstance;
    private boolean httpTracingEnabled = true;
    /** 是否桥接宿主 MeterRegistry（无 Micrometer/无 MeterRegistry 时自动跳过） */
    private boolean micrometerEnabled = true;
    private boolean diagnosticLogs = false;
    /**
     * 上报 Token（可选）；非空则带 X-Insight-Token
     */
    private String ingestToken = "";
    private String[] excludePatterns = new String[]{
            "/actuator/**",
            "/health",
            "/prometheus",
            "/favicon.ico"
    };

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceInstance() {
        return serviceInstance;
    }

    public void setServiceInstance(String serviceInstance) {
        this.serviceInstance = serviceInstance;
    }

    public boolean isHttpTracingEnabled() {
        return httpTracingEnabled;
    }

    public void setHttpTracingEnabled(boolean httpTracingEnabled) {
        this.httpTracingEnabled = httpTracingEnabled;
    }

    /**
     * @return 是否启用 Micrometer 联动
     */
    public boolean isMicrometerEnabled() {
        return micrometerEnabled;
    }

    /**
     * @param micrometerEnabled 是否启用 Micrometer 联动
     */
    public void setMicrometerEnabled(boolean micrometerEnabled) {
        this.micrometerEnabled = micrometerEnabled;
    }

    public boolean isDiagnosticLogs() {
        return diagnosticLogs;
    }

    public void setDiagnosticLogs(boolean diagnosticLogs) {
        this.diagnosticLogs = diagnosticLogs;
    }

    public String getIngestToken() {
        return ingestToken;
    }

    public void setIngestToken(String ingestToken) {
        this.ingestToken = ingestToken;
    }

    /**
     * @return 规范化 ingest token；未配置为空串
     */
    public String normalizedIngestToken() {
        return ingestToken == null ? "" : ingestToken.trim();
    }

    public String[] getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(String[] excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

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

    public boolean hasServerUrl() {
        return !normalizeServerUrl().isEmpty();
    }

    public String[] resolveExcludePatterns() {
        List<String> list = new ArrayList<String>(Arrays.asList(excludePatterns));
        return list.toArray(new String[0]);
    }

    public void resolveServiceNameFromEnvironment(Environment environment) {
        if (StringUtils.hasText(serviceName) || environment == null) {
            return;
        }
        String appName = environment.getProperty("spring.application.name");
        if (StringUtils.hasText(appName)) {
            this.serviceName = appName.trim();
        }
    }

    /**
     * 检查启用状态下是否已解析到服务名。
     * <p>
     * 须在 {@link #resolveServiceNameFromEnvironment(Environment)} 之后调用。
     * 未解析到服务名时不抛异常，将 enabled 置为 false，由调用方打 WARN。
     * </p>
     *
     * @return {@code true} 表示可以采集；{@code false} 表示已关闭或缺少服务名
     */
    public boolean validate() {
        if (!enabled) {
            return false;
        }
        if (!StringUtils.hasText(serviceName)) {
            // 只引入 Starter、未配 service-name / application.name 时允许启动
            this.enabled = false;
            return false;
        }
        return true;
    }
}
