package io.github.iweidujiang.springinsight.server.settings;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import javax.net.ssl.SSLHandshakeException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渠道试发失败文案统一。
 *
 * @author 苏渡苇
 * @since 2026-11-17
 */
class InsightSettingsControllerExplainErrorTest {

    /**
     * Network unreachable 时提示检查 insight-server 出网。
     */
    @Test
    void networkUnreachableMentionsOutbound() {
        String msg = InsightSettingsController.explainChannelSendError(
                "dingtalk", new ConnectException("Network unreachable"));
        assertTrue(msg.contains("oapi.dingtalk.com"));
        assertTrue(msg.contains("出网") || msg.contains("Docker"));
        assertTrue(msg.contains("Network unreachable"));
    }

    /**
     * TLS 握手失败给出证书 / 代理提示。
     */
    @Test
    void tlsFailureMentionsCertificate() {
        String msg = InsightSettingsController.explainChannelSendError(
                "feishu", new SSLHandshakeException("PKIX path building failed"));
        assertTrue(msg.contains("飞书") || msg.contains("TLS"));
        assertTrue(msg.contains("证书") || msg.contains("代理"));
    }

    /**
     * 发送器已返回中文配置提示时原样透传。
     */
    @Test
    void chineseConfigHintPassthrough() {
        String raw = "钉钉未配置完整：请填写 Webhook URL 与加签密钥";
        assertEquals(raw, InsightSettingsController.explainChannelSendError(
                "dingtalk", new IllegalStateException(raw)));
    }

    /**
     * 旧英文业务错误映射为中文。
     */
    @Test
    void legacyEnglishMapped() {
        String msg = InsightSettingsController.explainChannelSendError(
                "dingtalk", new IllegalStateException("dingtalk errcode"));
        assertTrue(msg.contains("钉钉"));
        assertTrue(msg.contains("errcode") || msg.contains("加签"));
        assertFalse(msg.contains("出网"));
    }

    /**
     * 邮件发送失败保留细节。
     */
    @Test
    void emailFailureKeepsDetail() {
        String msg = InsightSettingsController.explainChannelSendError(
                "email", new IllegalStateException("邮件发送失败：Connection refused"));
        assertTrue(msg.startsWith("邮件"));
        assertTrue(msg.contains("Connection refused"));
    }
}
