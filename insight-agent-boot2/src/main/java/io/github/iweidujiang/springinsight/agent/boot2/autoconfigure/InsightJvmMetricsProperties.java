package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Boot2 JVM 指标配置（{@code spring.insight.jvm-metrics.*}）。
 *
 * @author 苏渡苇
 * @since 2026-11-09
 */
@ConfigurationProperties(prefix = "spring.insight.jvm-metrics")
public class InsightJvmMetricsProperties {

    /**
     * 是否启用 JVM 指标采集与上报。默认关闭。
     */
    private boolean enabled = false;

    /**
     * 上报间隔（毫秒）。
     */
    private int reportInterval = 30000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getReportInterval() {
        return reportInterval;
    }

    public void setReportInterval(int reportInterval) {
        this.reportInterval = reportInterval;
    }
}
