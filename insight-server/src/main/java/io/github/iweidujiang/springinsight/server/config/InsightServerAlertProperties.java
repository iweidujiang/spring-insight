package io.github.iweidujiang.springinsight.server.config;

import io.github.iweidujiang.springinsight.server.alert.AlertCondition;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * insight-server 轻量告警配置（Webhook + 可选 SMTP 邮件；默认关闭）。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Data
@ConfigurationProperties(prefix = "spring.insight.server.alert")
public class InsightServerAlertProperties {

    /**
     * 是否启用告警扫描
     */
    private boolean enabled = false;

    /**
     * Webhook 目标 URL；与邮件二选一或同时配置
     */
    private String webhookUrl = "";

    /**
     * 指标：{@code error_rate}（百分比）、{@code error_count}（窗口内错误 Span 数）、
     * {@code latency_p95}（窗口内 p95 耗时，毫秒）。未配置 {@link #conditions} 时仍生效。
     *
     * @deprecated 请改用 {@link #conditions}
     */
    @Deprecated
    private String metric = "error_rate";

    /**
     * 阈值：error_rate 为百分比；error_count 为次数；latency_p95 为毫秒。
     * 未配置 {@link #conditions} 时按大于等于该值判断。
     *
     * @deprecated 请改用 {@link #conditions}
     */
    @Deprecated
    private double threshold = 10.0;

    /**
     * 组合条件。非空时优先于 {@link #metric} 和 {@link #threshold}，全部成立才告警。
     */
    private List<AlertCondition> conditions;

    /**
     * 统计窗口（分钟）
     */
    private int windowMinutes = 15;

    /**
     * 同一服务重复告警冷却（分钟）
     */
    private int cooldownMinutes = 30;

    /**
     * 是否启用静默时段（时段内不推送，扫描仍执行但不 deliver）
     */
    private boolean quietHoursEnabled = false;

    /**
     * 静默开始（本地时区），{@code HH:mm}，如 {@code 22:00}
     */
    private String quietHoursStart = "22:00";

    /**
     * 静默结束（本地时区），{@code HH:mm}；可跨午夜（如 22:00→08:00）
     */
    private String quietHoursEnd = "08:00";

    /**
     * 静默时段时区；空则用 JVM 默认时区
     */
    private String quietHoursZone = "";

    /**
     * SMTP 邮件通道（可填真实邮箱；默认关）
     */
    private Email email = new Email();

    /**
     * 钉钉群机器人（Webhook + 加签；默认关）
     */
    private Dingtalk dingtalk = new Dingtalk();

    /**
     * 飞书群机器人（Webhook，加签可选；默认关）
     */
    private Feishu feishu = new Feishu();

    /**
     * 企业微信群机器人（Webhook，无需加签；默认关）
     */
    private Wecom wecom = new Wecom();

    /**
     * SMTP / 收件配置。
     */
    @Data
    public static class Email {

        /**
         * 是否启用邮件通道（仍须 host / from / to 齐全）
         */
        private boolean enabled = false;

        /**
         * SMTP 主机，如 {@code smtp.qq.com}、{@code smtp.163.com}、{@code smtp.gmail.com}
         */
        private String host = "";

        /**
         * SMTP 端口；常见 587（STARTTLS）或 465（SSL）
         */
        private int port = 587;

        /**
         * SMTP 登录用户（多数邮箱与发件地址相同）
         */
        private String username = "";

        /**
         * SMTP 密码或授权码（勿提交真实值；用环境变量注入）
         */
        private String password = "";

        /**
         * 发件人地址
         */
        private String from = "";

        /**
         * 收件人，多个用英文逗号分隔
         */
        private String to = "";

        /**
         * 是否启用 STARTTLS（587 常用）
         */
        private boolean startTls = true;

        /**
         * 是否启用 SSL（465 常用；与 startTls 二选一）
         */
        private boolean ssl = false;
    }

    /**
     * 钉钉自定义机器人。{@code serverUrl} 是控制台根地址，用于消息里的「查看详情」。
     */
    @Data
    public static class Dingtalk {
        private boolean enabled = false;
        private String webhookUrl = "";
        private String secret = "";
        private String serverUrl = "";
    }

    /**
     * 飞书自定义机器人。未填 secret 时不签名。{@code serverUrl} 用于卡片里的「查看详情」。
     *
     * @author 苏渡苇
     * @since 2026-10-19
     */
    @Data
    public static class Feishu {
        private boolean enabled = false;
        private String webhookUrl = "";
        private String secret = "";
        private String serverUrl = "";
    }

    /**
     * 企业微信群机器人。{@code serverUrl} 用于 Markdown 里的「查看详情」。
     *
     * @author 苏渡苇
     * @since 2026-10-19
     */
    @Data
    public static class Wecom {
        private boolean enabled = false;
        private String webhookUrl = "";
        private String serverUrl = "";
    }

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
     * @return 实际参与判定的条件。已配置 conditions 时用它，否则把旧指标收成一条 gte
     */
    public List<AlertCondition> effectiveConditions() {
        return AlertCondition.resolve(conditions, normalizedMetric(), threshold);
    }

    /**
     * @return Webhook 通道是否可发
     */
    public boolean isWebhookSendReady() {
        return enabled && StringUtils.hasText(normalizedWebhookUrl());
    }

    /**
     * @return 邮件通道是否可发
     */
    public boolean isEmailSendReady() {
        if (!enabled || email == null || !email.isEnabled()) {
            return false;
        }
        return StringUtils.hasText(trim(email.getHost()))
                && StringUtils.hasText(trim(email.getFrom()))
                && StringUtils.hasText(trim(email.getTo()));
    }

    /**
     * @return 钉钉通道是否可发（须同时有 Webhook 与加签密钥）
     */
    public boolean isDingtalkSendReady() {
        if (!enabled || dingtalk == null || !dingtalk.isEnabled()) {
            return false;
        }
        return StringUtils.hasText(trim(dingtalk.getWebhookUrl()))
                && StringUtils.hasText(trim(dingtalk.getSecret()));
    }

    /**
     * @return 飞书通道是否可发（须有 Webhook；密钥可选）
     */
    public boolean isFeishuSendReady() {
        if (!enabled || feishu == null || !feishu.isEnabled()) {
            return false;
        }
        return StringUtils.hasText(trim(feishu.getWebhookUrl()));
    }

    /**
     * @return 企业微信通道是否可发（须有 Webhook）
     */
    public boolean isWecomSendReady() {
        if (!enabled || wecom == null || !wecom.isEnabled()) {
            return false;
        }
        return StringUtils.hasText(trim(wecom.getWebhookUrl()));
    }

    /**
     * @return 是否至少有一个发送通道就绪
     */
    public boolean isSendReady() {
        return isWebhookSendReady() || isEmailSendReady() || isDingtalkSendReady()
                || isFeishuSendReady() || isWecomSendReady();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
