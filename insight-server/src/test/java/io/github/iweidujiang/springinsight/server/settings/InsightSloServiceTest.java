package io.github.iweidujiang.springinsight.server.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerAiProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.server.slo.InsightSloService;
import io.github.iweidujiang.springinsight.server.slo.ServiceSlo;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SLO CRUD 持久化与错误预算集成。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
class InsightSloServiceTest {

    @TempDir
    Path tempDir;

    private InsightRuntimeSettingsService settingsService;
    private InsightSloService sloService;
    private TraceSpanPersistenceService persistence;

    /**
     * 临时数据目录 + 内存 Span。
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

        InsightServerStorageProperties mem = new InsightServerStorageProperties();
        InMemorySpanStore spanStore = new InMemorySpanStore(mem, "memory");
        persistence = new TraceSpanPersistenceService(spanStore);
        sloService = new InsightSloService(settingsService, persistence);
    }

    /**
     * 创建 / 列表 / 更新 / 删除。
     */
    @Test
    void crud_roundTrip() {
        ServiceSlo created = sloService.create(slo("sca-user", 0.999, 720));
        assertEquals("sca-user", created.getServiceName());
        assertEquals(1, sloService.list().size());

        assertThrows(IllegalStateException.class, () -> sloService.create(slo("sca-user", 0.99, 24)));

        ServiceSlo updated = sloService.update("sca-user", slo("sca-user", 0.995, 168));
        assertEquals(0.995, updated.getTargetAvailability(), 1e-9);
        assertEquals(168L, updated.getWindowHours());

        assertTrue(sloService.delete("sca-user"));
        assertTrue(sloService.list().isEmpty());
        assertFalse(sloService.delete("sca-user"));
    }

    /**
     * 写入 runtime-settings.json 后重启服务仍可读。
     */
    @Test
    void survivesRestart() throws Exception {
        sloService.create(slo("order", 0.9999, 720));
        Path path = Path.of(settingsService.publicView().get("settingsPath").toString());
        assertTrue(Files.exists(path), "settings file missing: " + path);

        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        storage.setMode("file");
        storage.setFilePath(tempDir.resolve("spans.json").toString());
        InsightRuntimeSettingsStore store = new InsightRuntimeSettingsStore(storage, new ObjectMapper());
        InsightRuntimeSettingsService reloaded = new InsightRuntimeSettingsService(
                store, new InsightServerAlertProperties(), new InsightServerAiProperties());
        reloaded.init();
        InsightSloService again = new InsightSloService(reloaded, persistence);
        assertEquals(1, again.list().size());
        assertEquals("order", again.list().get(0).getServiceName());
        assertEquals(0.9999, again.list().get(0).getTargetAvailability(), 1e-9);
        var patch = reloaded.snapshot();
        patch.getAi().setModel("keep-slo");
        reloaded.updateAndApply(patch);
        assertEquals(1, again.list().size());
        assertEquals("keep-slo", reloaded.effectiveAi().getModel());
    }

    /**
     * 有错误流量时预算计算与状态正确。
     */
    @Test
    void budget_fromSpans() {
        sloService.create(slo("sca-user", 0.999, 720));
        long now = System.currentTimeMillis();
        List<TraceSpan> spans = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            TraceSpan ok = new TraceSpan();
            ok.setServiceName("sca-user");
            ok.setSuccess(true);
            ok.setStartTime(now - 60_000L);
            ok.setDurationMs(10L);
            spans.add(ok);
        }
        for (int i = 0; i < 2; i++) {
            TraceSpan err = new TraceSpan();
            err.setServiceName("sca-user");
            err.setSuccess(false);
            err.setStatusCode("ERROR");
            err.setStartTime(now - 30_000L);
            err.setDurationMs(10L);
            spans.add(err);
        }
        persistence.saveTraceSpans(spans);

        Map<String, Object> budget = sloService.budget("sca-user", 24).orElseThrow();
        assertEquals("sca-user", budget.get("service"));
        assertEquals(0.999, ((Number) budget.get("target")).doubleValue(), 1e-9);
        assertEquals("exhausted", budget.get("status"));
        assertTrue(((Number) budget.get("budgetConsumed")).doubleValue() > 0.01);
    }

    private static ServiceSlo slo(String name, double target, long hours) {
        ServiceSlo s = new ServiceSlo();
        s.setServiceName(name);
        s.setTargetAvailability(target);
        s.setWindowHours(hours);
        return s;
    }
}
