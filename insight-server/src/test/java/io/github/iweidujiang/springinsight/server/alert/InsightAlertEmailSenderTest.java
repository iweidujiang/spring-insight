package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邮件告警辅助逻辑与未就绪时短路。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class InsightAlertEmailSenderTest {

    /**
     * 收件人按逗号拆分并去空白。
     */
    @Test
    void splitAddresses() {
        assertArrayEquals(new String[]{"a@x.com", "b@y.com"},
                InsightAlertEmailSender.splitAddresses(" a@x.com , b@y.com "));
    }

    /**
     * 正文为 key: value 行；有 AI 摘要时优先展示。
     */
    @Test
    void formatBody() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serviceName", "sca-order");
        payload.put("value", 3.0);
        assertEquals("serviceName: sca-order\nvalue: 3.0", InsightAlertEmailSender.formatBody(payload));
    }

    /**
     * AI 摘要与建议排在正文前部。
     */
    @Test
    void formatBodyWithAi() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serviceName", "sca-order");
        payload.put("aiSummary", "错误率飙升");
        payload.put("aiSuggestions", java.util.List.of("查下游", "看 Trace"));
        payload.put("value", 12.0);
        String body = InsightAlertEmailSender.formatBody(payload);
        assertTrue(body.startsWith("AI 解读: 错误率飙升"));
        assertTrue(body.contains("1. 查下游"));
        assertTrue(body.contains("serviceName: sca-order"));
        assertFalse(body.contains("aiSummary:"));
    }

    /**
     * SSL 与 STARTTLS 属性互斥设置。
     */
    @Test
    void buildSenderSslVsStartTls() {
        InsightServerAlertProperties.Email email = new InsightServerAlertProperties.Email();
        email.setHost("smtp.qq.com");
        email.setPort(465);
        email.setUsername("u");
        email.setPassword("p");
        email.setSsl(true);
        email.setStartTls(false);
        JavaMailSenderImpl ssl = InsightAlertEmailSender.buildSender(email);
        assertEquals("true", ssl.getJavaMailProperties().getProperty("mail.smtp.ssl.enable"));

        email.setPort(587);
        email.setSsl(false);
        email.setStartTls(true);
        JavaMailSenderImpl startTls = InsightAlertEmailSender.buildSender(email);
        assertEquals("true", startTls.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"));
    }

    /**
     * 邮件未就绪时 send 直接 false，不抛异常。
     */
    @Test
    void sendReturnsFalseWhenNotReady() {
        InsightServerAlertProperties props = new InsightServerAlertProperties();
        props.setEnabled(true);
        InsightAlertEmailSender sender = new InsightAlertEmailSender();
        assertFalse(sender.send(props, Map.of("serviceName", "x")));
    }

    /**
     * 配置齐全时 isEmailSendReady 为 true。
     */
    @Test
    void emailReadyRequiresHostFromTo() {
        InsightServerAlertProperties props = new InsightServerAlertProperties();
        props.setEnabled(true);
        InsightServerAlertProperties.Email email = new InsightServerAlertProperties.Email();
        email.setEnabled(true);
        email.setHost("smtp.163.com");
        email.setFrom("a@163.com");
        email.setTo("b@163.com");
        props.setEmail(email);
        assertTrue(props.isEmailSendReady());
    }
}
