package io.github.iweidujiang.springinsight.server.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
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
