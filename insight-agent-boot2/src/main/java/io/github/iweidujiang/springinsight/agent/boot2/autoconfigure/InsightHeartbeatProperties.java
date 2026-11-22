package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 存活心跳配置（{@code spring.insight.heartbeat.*}）。
 * <p>
 * 低流量服务无 Span 时仍可定期向监测中心报「还活着」；默认开启、间隔偏温和。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026-11-20
 */
@ConfigurationProperties(prefix = "spring.insight.heartbeat")
public class InsightHeartbeatProperties {

    /**
     * 是否启用心跳上报。默认 true；需已配置 {@code spring.insight.server-url}。
     */
    private boolean enabled = true;

    /**
     * 心跳间隔（毫秒）。默认 60000（1 分钟）。
     */
    private int intervalMs = 60_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(int intervalMs) {
        this.intervalMs = intervalMs;
    }
}
