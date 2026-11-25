package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 通过自定义 SMTP 将告警发到真实邮箱。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@Component
public class InsightAlertEmailSender implements InsightAlertChannelSender {

    @Override
    public String channelType() {
        return "email";
    }

    /**
     * 按渠道配置发邮件。未就绪或 SMTP 失败时抛出异常。
     */
    @Override
    public void send(AlertPayload payload, ChannelConfig config) throws Exception {
        sendOrThrow(config.alertProperties(), payload.fields());
    }

    /**
     * 使用调用方传入的生效配置发送邮件。
     *
     * @param properties 生效中的告警配置
     * @param payload    与 Webhook 相同的字段 Map
     * @return 发送成功为 true；未就绪或失败为 false（调度侧吞掉细节）
     */
    public boolean send(InsightServerAlertProperties properties, Map<String, Object> payload) {
        try {
            sendOrThrow(properties, payload);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 发送邮件；配置不全或 SMTP 失败时抛出带可读信息的异常（供试发接口展示）。
     *
     * @param properties 生效配置
     * @param payload    告警字段
     * @throws Exception 未就绪或发送失败
     */
    void sendOrThrow(InsightServerAlertProperties properties, Map<String, Object> payload) throws Exception {
        if (properties == null || !properties.isEmailSendReady()) {
            throw new IllegalStateException("邮件未配置完整：请填写 SMTP Host、发件人 From 与收件人 To");
        }
        InsightServerAlertProperties.Email email = properties.getEmail();
        try {
            JavaMailSenderImpl sender = buildSender(email);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(email.getFrom().trim());
            message.setTo(splitAddresses(email.getTo()));
            String service = String.valueOf(payload.getOrDefault("serviceName", "unknown"));
            message.setSubject("[Spring Insight] alert: " + service);
            message.setText(formatBody(payload));
            sender.send(message);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[告警] 邮件发送失败: to={}, error={}", email.getTo(), e.getMessage());
            String detail = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage().trim();
            throw new IllegalStateException("邮件发送失败：" + detail, e);
        }
    }

    /**
     * @param email SMTP 配置
     * @return 按本次配置构建的 MailSender（不依赖 spring.mail.*）
     */
    static JavaMailSenderImpl buildSender(InsightServerAlertProperties.Email email) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(email.getHost().trim());
        sender.setPort(email.getPort() > 0 ? email.getPort() : 587);
        if (StringUtils.hasText(email.getUsername())) {
            sender.setUsername(email.getUsername().trim());
        }
        if (StringUtils.hasText(email.getPassword())) {
            sender.setPassword(email.getPassword());
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        boolean auth = StringUtils.hasText(email.getUsername());
        props.put("mail.smtp.auth", Boolean.toString(auth));
        if (email.isSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
        } else {
            props.put("mail.smtp.ssl.enable", "false");
            props.put("mail.smtp.starttls.enable", Boolean.toString(email.isStartTls()));
            props.put("mail.smtp.starttls.required", Boolean.toString(email.isStartTls()));
        }
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        return sender;
    }

    /**
     * @param to 逗号分隔收件人
     * @return 地址数组
     */
    static String[] splitAddresses(String to) {
        return Arrays.stream(to.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }

    /**
     * @param payload 告警字段
     * @return 邮件正文（AI 摘要优先展示）
     */
    static String formatBody(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder();
        Object summary = payload.get("aiSummary");
        if (summary != null && !String.valueOf(summary).isBlank()) {
            sb.append("AI 解读: ").append(summary).append('\n');
            Object suggestions = payload.get("aiSuggestions");
            if (suggestions instanceof List<?> list && !list.isEmpty()) {
                sb.append("建议:\n");
                int i = 1;
                for (Object item : list) {
                    sb.append("  ").append(i++).append(". ").append(item).append('\n');
                }
            }
            sb.append('\n');
        }
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            String k = e.getKey();
            if ("aiSummary".equals(k) || "aiSuggestions".equals(k)
                    || "consoleLinks".equals(k) || "primaryUrl".equals(k)
                    || "tracesPath".equals(k) || "servicePath".equals(k)) {
                continue;
            }
            sb.append(k).append(": ").append(e.getValue()).append('\n');
        }
        appendConsoleLinks(sb, payload);
        return sb.toString().trim();
    }

    /**
     * 邮件末尾附带控制台深链。
     *
     * @param sb      正文
     * @param payload 告警字段
     */
    static void appendConsoleLinks(StringBuilder sb, Map<String, Object> payload) {
        if (payload == null) {
            return;
        }
        Object primary = payload.get("primaryUrl");
        Object traces = null;
        Object service = null;
        Object linksObj = payload.get("consoleLinks");
        if (linksObj instanceof Map<?, ?> links) {
            if (primary == null) {
                primary = links.get("primaryUrl");
            }
            traces = links.get("tracesUrl");
            service = links.get("serviceUrl");
            if (traces == null) {
                traces = links.get("tracesPath");
            }
            if (service == null) {
                service = links.get("servicePath");
            }
        }
        if (primary == null && traces == null && service == null) {
            return;
        }
        sb.append("\n\n打开控制台:\n");
        if (primary != null && !String.valueOf(primary).isBlank()) {
            sb.append("  主入口: ").append(primary).append('\n');
        }
        if (traces != null && !String.valueOf(traces).isBlank()
                && !String.valueOf(traces).equals(String.valueOf(primary))) {
            sb.append("  错误链路: ").append(traces).append('\n');
        }
        if (service != null && !String.valueOf(service).isBlank()
                && !String.valueOf(service).equals(String.valueOf(primary))) {
            sb.append("  服务详情: ").append(service).append('\n');
        }
    }
}
