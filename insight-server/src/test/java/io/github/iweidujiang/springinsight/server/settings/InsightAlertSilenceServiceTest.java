package io.github.iweidujiang.springinsight.server.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.server.alert.InsightAlertSilenceService;
import io.github.iweidujiang.springinsight.server.alert.SilenceRule;
import io.github.iweidujiang.springinsight.server.config.InsightServerAiProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 静默规则 CRUD 与持久化。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
class InsightAlertSilenceServiceTest {

    @TempDir
    Path tempDir;

    private InsightRuntimeSettingsService settingsService;
    private InsightAlertSilenceService silenceService;

    /**
     * 临时数据目录。
     */
    @BeforeEach
    void setUp() {
        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        storage.setMode("file");
        storage.setFilePath(tempDir.resolve("spans.json").toString());
        InsightRuntimeSettingsStore store = new InsightRuntimeSettingsStore(storage, new ObjectMapper());
        settingsService = new InsightRuntimeSettingsService(
                store, new InsightServerAlertProperties(), new InsightServerAiProperties());
        settingsService.init();
        silenceService = new InsightAlertSilenceService(settingsService);
    }

    /**
     * 创建 / 列表 / 删除；非法时间拒绝。
     */
    @Test
    void crud_roundTrip() {
        SilenceRule created = silenceService.create(rule("sca-order", "error_rate", "22:00", "08:00"));
        assertFalse(created.getId().isBlank());
        assertEquals("sca-order", created.getServiceName());
        assertEquals("error_rate", created.getMetric());
        assertEquals(1, silenceService.list().size());

        SilenceRule bad = rule("", "", "25:00", "08:00");
        assertThrows(IllegalArgumentException.class, () -> silenceService.create(bad));

        assertTrue(silenceService.delete(created.getId()));
        assertTrue(silenceService.list().isEmpty());
        assertFalse(silenceService.delete(created.getId()));
    }

    /**
     * 写入设置文件后重启仍可读；设置页合并不丢规则。
     */
    @Test
    void survivesRestartAndSettingsMerge() throws Exception {
        SilenceRule created = silenceService.create(rule("order", "", "00:00", "06:00"));
        Path path = Path.of(settingsService.publicView().get("settingsPath").toString());
        assertTrue(Files.exists(path));

        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        storage.setMode("file");
        storage.setFilePath(tempDir.resolve("spans.json").toString());
        InsightRuntimeSettingsStore store = new InsightRuntimeSettingsStore(storage, new ObjectMapper());
        InsightRuntimeSettingsService reloaded = new InsightRuntimeSettingsService(
                store, new InsightServerAlertProperties(), new InsightServerAiProperties());
        reloaded.init();
        InsightAlertSilenceService again = new InsightAlertSilenceService(reloaded);
        assertEquals(1, again.list().size());
        assertEquals(created.getId(), again.list().get(0).getId());

        var patch = reloaded.snapshot();
        patch.getAlert().setEnabled(true);
        patch.getAlert().setSilences(null);
        reloaded.updateAndApply(patch);
        assertEquals(1, again.list().size());
    }

    private static SilenceRule rule(String service, String metric, String start, String end) {
        SilenceRule r = new SilenceRule();
        r.setServiceName(service);
        r.setMetric(metric);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setTimezone("Asia/Shanghai");
        r.setReason("test");
        return r;
    }
}
