package io.github.iweidujiang.springinsight.server.settings;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渠道试发出网失败文案。
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
     * 普通业务错误原样返回。
     */
    @Test
    void businessErrorUnchanged() {
        String msg = InsightSettingsController.explainChannelSendError(
                "dingtalk", new IllegalStateException("dingtalk errcode"));
        assertTrue(msg.contains("dingtalk errcode"));
        assertFalse(msg.contains("出网"));
    }
}
