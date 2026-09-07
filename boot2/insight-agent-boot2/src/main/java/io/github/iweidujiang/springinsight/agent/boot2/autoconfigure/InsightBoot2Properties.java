package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Boot2 线配置（{@code spring.insight.*}），字段与主线对齐的子集。
 */
@ConfigurationProperties(prefix = "spring.insight")
public class InsightBoot2Properties {

    /** 总开关 */
    private boolean enabled = true;

    /** Insight Server 根地址，例如 http://localhost:9966 */
    private String serverUrl;

    /** 服务名；空则装配时回退 spring.application.name */
    private String serviceName;

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
}
