package io.github.iweidujiang.springinsight.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.3 配置属性默认值与就绪判断。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class InsightServerFeaturePropertiesTest {

    /**
     * Alert 默认关闭且无 URL 时不可发送。
     */
    @Test
    void alertDefaultsClosed() {
        InsightServerAlertProperties p = new InsightServerAlertProperties();
        assertFalse(p.isEnabled());
        assertFalse(p.isSendReady());
        assertEquals("error_rate", p.normalizedMetric());
        assertEquals(10.0, p.getThreshold());
        assertEquals(15, p.getWindowMinutes());
        assertEquals(30, p.getCooldownMinutes());
    }

    /**
     * Alert 开启且配置 URL 后就绪。
     */
    @Test
    void alertSendReadyWhenEnabledWithUrl() {
        InsightServerAlertProperties p = new InsightServerAlertProperties();
        p.setEnabled(true);
        p.setWebhookUrl(" https://example.com/hook ");
        assertTrue(p.isSendReady());
        assertEquals("https://example.com/hook", p.normalizedWebhookUrl());
    }

    /**
     * AI 默认关闭且无 key 时不可调用。
     */
    @Test
    void aiDefaultsClosed() {
        InsightServerAiProperties p = new InsightServerAiProperties();
        assertFalse(p.isEnabled());
        assertFalse(p.isInvokeReady());
        assertEquals("openai-compatible", p.getProvider());
        assertEquals("gpt-4o-mini", p.getModel());
    }

    /**
     * AI 开启且 key、base-url 齐全时就绪；base-url 去尾斜杠。
     */
    @Test
    void aiInvokeReadyWhenConfigured() {
        InsightServerAiProperties p = new InsightServerAiProperties();
        p.setEnabled(true);
        p.setApiKey("sk-test");
        p.setBaseUrl("https://api.openai.com/v1/");
        assertTrue(p.isInvokeReady());
        assertEquals("https://api.openai.com/v1", p.normalizedBaseUrl());
    }
}
