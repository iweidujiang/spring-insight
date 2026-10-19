package io.github.iweidujiang.springinsight.server.settings;

import lombok.Data;

/**
 * 控制台可改的运行时设置（告警 + AI）；持久化到数据目录 JSON。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Data
public class InsightRuntimeSettings {

    private AlertSettings alert = new AlertSettings();
    private AiSettings ai = new AiSettings();

    /**
     * 告警设置。
     */
    @Data
    public static class AlertSettings {
        private boolean enabled = false;
        private String webhookUrl = "";
        private String metric = "error_rate";
        private double threshold = 10.0;
        private int windowMinutes = 15;
        private int cooldownMinutes = 30;
        /** 静默时段（本地时区 HH:mm；可跨午夜） */
        private boolean quietHoursEnabled = false;
        private String quietHoursStart = "22:00";
        private String quietHoursEnd = "08:00";
        /** 空 = JVM 默认时区 */
        private String quietHoursZone = "";
        private EmailSettings email = new EmailSettings();
        /** 缺省为 null，避免旧控制台保存时把未提交的钉钉配置清空 */
        private DingtalkSettings dingtalk;
        /** 缺省为 null，避免旧控制台保存时把未提交的飞书配置清空 */
        private FeishuSettings feishu;
        /** 缺省为 null，避免旧控制台保存时把未提交的企业微信配置清空 */
        private WecomSettings wecom;
    }

    /**
     * 企业微信机器人。
     *
     * @author 苏渡苇
     * @since 2026-10-19
     */
    @Data
    public static class WecomSettings {
        private boolean enabled = false;
        private String webhookUrl = "";
        private String serverUrl = "";
    }

    /**
     * 飞书机器人。密钥只进本地设置文件，接口响应不回传明文。
     *
     * @author 苏渡苇
     * @since 2026-10-19
     */
    @Data
    public static class FeishuSettings {
        private boolean enabled = false;
        private String webhookUrl = "";
        private String secret = "";
        private String serverUrl = "";
    }

    /**
     * 钉钉机器人。密钥只进本地设置文件，接口响应不回传明文。
     */
    @Data
    public static class DingtalkSettings {
        private boolean enabled = false;
        private String webhookUrl = "";
        private String secret = "";
        private String serverUrl = "";
    }

    /**
     * SMTP 设置。
     */
    @Data
    public static class EmailSettings {
        private boolean enabled = false;
        private String host = "";
        private int port = 587;
        private String username = "";
        /** 明文仅存文件；API 响应不回传 */
        private String password = "";
        private String from = "";
        private String to = "";
        private boolean startTls = true;
        private boolean ssl = false;
    }

    /**
     * AI 设置。
     */
    @Data
    public static class AiSettings {
        private boolean enabled = false;
        private String provider = "openai-compatible";
        private String baseUrl = "https://api.openai.com/v1";
        /** 明文仅存文件；API 响应不回传 */
        private String apiKey = "";
        private String model = "gpt-4o-mini";
        private int timeoutMs = 30000;
        private int maxInputSpans = 40;
        private int maxTokens = 800;
        /** 告警推送是否附带 AI 短解读 */
        private boolean attachToAlerts = false;
        /** 告警 AI 每小时最多调用次数 */
        private int alertMaxPerHour = 10;
        /** 时段 AI 小结每小时最多调用次数 */
        private int periodMaxPerHour = 20;
    }
}
