package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * 通过自定义 SMTP 将告警发到真实邮箱。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@Component
public class InsightAlertEmailSender {

    /**
     * 使用调用方传入的生效配置发送邮件。
     *
     * @param properties 生效中的告警配置
     * @param payload    与 Webhook 相同的字段 Map
     * @return 发送成功为 true
     */
    public boolean send(InsightServerAlertProperties properties, Map<String, Object> payload) {
        if (properties == null || !properties.isEmailSendReady()) {
            return false;
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
            return true;
        } catch (Exception e) {
            log.warn("[告警] 邮件发送失败: to={}, error={}", email.getTo(), e.getMessage());
            return false;
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
     * @return 邮件正文
     */
    static String formatBody(Map<String, Object> payload) {
        return payload.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
    }
}
