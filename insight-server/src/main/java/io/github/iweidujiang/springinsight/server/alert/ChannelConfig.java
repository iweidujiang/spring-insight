package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;

/**
 * 一次投递可用的渠道配置。Webhook 用地址，邮件用整份生效中的告警配置。
 *
 * @param webhookUrl      规范化后的 Webhook 地址
 * @param alertProperties 生效中的告警配置（含 SMTP）
 */
public record ChannelConfig(String webhookUrl, InsightServerAlertProperties alertProperties) {

    public ChannelConfig {
        webhookUrl = webhookUrl == null ? "" : webhookUrl;
    }
}
