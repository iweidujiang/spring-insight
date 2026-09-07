/*
 * Copyright (c) 2026, 苏渡苇. All rights reserved.
 *
 * InsightBoot2Properties：Boot2 线 spring.insight.* 配置。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "spring.insight")
public class InsightBoot2Properties {

    private boolean enabled = true;
    private String serverUrl;
    private String serviceName;
    private String serviceInstance;
    private boolean httpTracingEnabled = true;
    private boolean diagnosticLogs = false;
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

    public boolean isDiagnosticLogs() {
        return diagnosticLogs;
    }

    public void setDiagnosticLogs(boolean diagnosticLogs) {
        this.diagnosticLogs = diagnosticLogs;
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

    public void validate() {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(serviceName)) {
            throw new IllegalArgumentException(
                    "spring.insight.service-name 为空且无法从 spring.application.name 回退");
        }
    }
}
