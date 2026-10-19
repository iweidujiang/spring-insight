package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;

/**
 * 一次投递可用的渠道配置。Webhook 用地址，钉钉另用加签密钥，邮件用整份生效中的告警配置。
 *
 * @param webhookUrl      该渠道的 Webhook 地址
 * @param alertProperties 生效中的告警配置（含 SMTP 与钉钉控制台地址）
 * @param secret          钉钉加签密钥；其他渠道为空
 */
public record ChannelConfig(String webhookUrl, InsightServerAlertProperties alertProperties, String secret) {

    public ChannelConfig(String webhookUrl, InsightServerAlertProperties alertProperties) {
        this(webhookUrl, alertProperties, "");
    }

    public ChannelConfig {
        webhookUrl = webhookUrl == null ? "" : webhookUrl;
        secret = secret == null ? "" : secret;
    }
}
