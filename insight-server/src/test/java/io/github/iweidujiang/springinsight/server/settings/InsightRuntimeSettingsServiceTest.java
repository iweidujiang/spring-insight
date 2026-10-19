package io.github.iweidujiang.springinsight.server.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.server.alert.AlertCondition;
import io.github.iweidujiang.springinsight.server.config.InsightServerAiProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运行时设置：文件读写与空密钥不覆盖。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class InsightRuntimeSettingsServiceTest {

    @TempDir
    Path tempDir;

    /**
     * 空 password / apiKey 保留原值；非空则覆盖。
     */
    @Test
    void emptySecretsDoNotOverwrite() {
        InsightRuntimeSettingsService service = newService();
        InsightRuntimeSettings first = new InsightRuntimeSettings();
        first.getAlert().setEnabled(true);
        first.getAlert().getEmail().setEnabled(true);
        first.getAlert().getEmail().setPassword("secret-smtp");
        first.getAi().setEnabled(true);
        first.getAi().setApiKey("sk-original");
        first.getAi().setBaseUrl("https://api.deepseek.com/v1");
        service.updateAndApply(first);

        InsightRuntimeSettings patch = new InsightRuntimeSettings();
        patch.getAlert().setEnabled(true);
        patch.getAlert().getEmail().setEnabled(true);
        patch.getAlert().getEmail().setPassword("");
        patch.getAlert().getEmail().setHost("smtp.example.com");
        patch.getAi().setEnabled(true);
        patch.getAi().setApiKey("");
        patch.getAi().setModel("deepseek-chat");
        Map<String, Object> view = service.updateAndApply(patch);

        assertEquals("secret-smtp", service.snapshot().getAlert().getEmail().getPassword());
        assertEquals("sk-original", service.snapshot().getAi().getApiKey());
        assertEquals("smtp.example.com", service.effectiveAlert().getEmail().getHost());
        assertEquals("deepseek-chat", service.effectiveAi().getModel());

        @SuppressWarnings("unchecked")
        Map<String, Object> email = (Map<String, Object>) ((Map<?, ?>) view.get("alert")).get("email");
        assertTrue((Boolean) email.get("passwordConfigured"));
        assertFalse(email.containsKey("password"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ai = (Map<String, Object>) view.get("ai");
        assertTrue((Boolean) ai.get("apiKeyConfigured"));
        assertFalse(ai.containsKey("apiKey"));
    }

    /**
     * 仅 runtime 文件开启 AI / 告警时，effective* 可读到开关。
     */
    @Test
    void effectiveReadsRuntimeFileOverBootDefaults() {
        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        storage.setMode("file");
        storage.setFilePath(tempDir.resolve("spans.json").toString());
        InsightRuntimeSettingsStore store = new InsightRuntimeSettingsStore(storage, new ObjectMapper());

        InsightRuntimeSettings file = new InsightRuntimeSettings();
        file.getAlert().setEnabled(true);
        file.getAlert().setWebhookUrl("http://127.0.0.1:9/hook");
        file.getAi().setEnabled(true);
        file.getAi().setApiKey("sk-runtime");
        store.save(file);

        InsightServerAlertProperties bootAlert = new InsightServerAlertProperties();
        bootAlert.setEnabled(false);
        InsightServerAiProperties bootAi = new InsightServerAiProperties();
        bootAi.setEnabled(false);
        InsightRuntimeSettingsService service = new InsightRuntimeSettingsService(store, bootAlert, bootAi);
        service.init();

        assertTrue(service.effectiveAlert().isEnabled());
        assertEquals("http://127.0.0.1:9/hook", service.effectiveAlert().getWebhookUrl());
        assertTrue(service.effectiveAi().isEnabled());
        assertEquals("sk-runtime", service.effectiveAi().getApiKey());
    }

    /**
     * 渠道密钥留空时保留原值，并出现在脱敏视图里。
     */
    @Test
    void channelSecretBlankKeepsSavedValue() {
        InsightRuntimeSettingsService service = newService();
        InsightRuntimeSettings.ChannelConfig dingtalk = new InsightRuntimeSettings.ChannelConfig();
        dingtalk.setId("dingtalk");
        dingtalk.setType("dingtalk");
        dingtalk.setEnabled(true);
        dingtalk.setName("钉钉");
        dingtalk.setWebhookUrl("https://oapi.dingtalk.com/robot/send?access_token=abc");
        dingtalk.setSecret("SEC-keep");
        dingtalk.setServerUrl("http://127.0.0.1:9966");
        InsightRuntimeSettings first = new InsightRuntimeSettings();
        first.setChannels(java.util.List.of(dingtalk));
        service.updateAndApply(first);

        InsightRuntimeSettings.ChannelConfig again = new InsightRuntimeSettings.ChannelConfig();
        again.setId("dingtalk");
        again.setType("dingtalk");
        again.setEnabled(true);
        again.setWebhookUrl("https://oapi.dingtalk.com/robot/send?access_token=abc");
        again.setSecret("");
        InsightRuntimeSettings patch = new InsightRuntimeSettings();
        patch.setChannels(java.util.List.of(again));
        Map<String, Object> view = service.updateAndApply(patch);

        assertEquals("SEC-keep", service.effectiveChannels().get(0).getSecret());
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> channels =
                (java.util.List<Map<String, Object>>) view.get("channels");
        assertEquals(1, channels.size());
        assertEquals(Boolean.TRUE, channels.get(0).get("secretConfigured"));
        assertFalse(channels.get(0).containsKey("secret"));
    }

    /**
     * 旧文件只有指标和阈值时，按一条大于等于条件生效；显式 conditions 优先于旧字段。
     */
    @Test
    void legacyMetricYieldsToExplicitConditions() throws Exception {
        String legacy = """
                {"alert":{"enabled":true,"metric":"error_count","threshold":3.0}}
                """;
        InsightRuntimeSettings parsed = new ObjectMapper().readValue(legacy, InsightRuntimeSettings.class);
        assertTrue(parsed.getAlert().getConditions() == null || parsed.getAlert().getConditions().isEmpty());
        assertEquals("error_count", AlertCondition.resolve(
                parsed.getAlert().getConditions(),
                parsed.getAlert().getMetric(),
                parsed.getAlert().getThreshold()).get(0).getMetric());

        InsightRuntimeSettingsService service = newService();
        AlertCondition latency = new AlertCondition();
        latency.setMetric("latency_p95");
        latency.setOperator("gt");
        latency.setThreshold(200);
        InsightRuntimeSettings withRules = new InsightRuntimeSettings();
        withRules.getAlert().setMetric("error_rate");
        withRules.getAlert().setThreshold(10);
        withRules.getAlert().setConditions(java.util.List.of(latency));
        service.updateAndApply(withRules);

        assertEquals("latency_p95", service.effectiveAlert().effectiveConditions().get(0).getMetric());
        assertEquals("gt", service.effectiveAlert().effectiveConditions().get(0).getOperator());

        InsightRuntimeSettings metricOnly = new InsightRuntimeSettings();
        metricOnly.getAlert().setMetric("error_count");
        metricOnly.getAlert().setThreshold(4);
        service.updateAndApply(metricOnly);
        assertEquals("latency_p95", service.effectiveAlert().effectiveConditions().get(0).getMetric());
    }

    private InsightRuntimeSettingsService newService() {
        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        storage.setMode("file");
        storage.setFilePath(tempDir.resolve("spans.json").toString());
        InsightRuntimeSettingsStore store = new InsightRuntimeSettingsStore(storage, new ObjectMapper());
        InsightRuntimeSettingsService service = new InsightRuntimeSettingsService(
                store, new InsightServerAlertProperties(), new InsightServerAiProperties());
        service.init();
        return service;
    }
}
