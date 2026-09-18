package io.github.iweidujiang.springinsight.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * insight-server 轻量 Webhook 告警配置（默认关闭）。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Data
@ConfigurationProperties(prefix = "spring.insight.server.alert")
public class InsightServerAlertProperties {

    /**
     * 是否启用告警扫描与 Webhook 推送
     */
    private boolean enabled = false;

    /**
     * Webhook 目标 URL；enabled 时须非空才实际发送
     */
    private String webhookUrl = "";

    /**
     * 指标：{@code error_rate}（百分比）或 {@code error_count}（窗口内错误 Span 数）
     */
    private String metric = "error_rate";

    /**
     * 阈值：error_rate 时为百分比；error_count 时为次数
     */
    private double threshold = 10.0;

    /**
     * 统计窗口（分钟）
     */
    private int windowMinutes = 15;

    /**
     * 同一服务重复告警冷却（分钟）
     */
    private int cooldownMinutes = 30;

    /**
     * @return 规范化后的 webhook URL
     */
    public String normalizedWebhookUrl() {
        return webhookUrl == null ? "" : webhookUrl.trim();
    }

    /**
     * @return 规范化指标名（小写）
     */
    public String normalizedMetric() {
        return metric == null ? "error_rate" : metric.trim().toLowerCase();
    }

    /**
     * @return 是否具备发送条件（开关开且 URL 非空）
     */
    public boolean isSendReady() {
        return enabled && StringUtils.hasText(normalizedWebhookUrl());
    }
}
