package io.github.iweidujiang.springinsight.server.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.server.config.InsightServerAiProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.server.dashboard.DashboardLayout;
import io.github.iweidujiang.springinsight.server.dashboard.DashboardLayouts;
import io.github.iweidujiang.springinsight.server.dashboard.DashboardPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仪表盘布局：默认值、保存持久化、模板列表。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
class DashboardLayoutPersistenceTest {

    @TempDir
    Path tempDir;

    private InsightRuntimeSettingsService settingsService;

    @BeforeEach
    void setUp() {
        settingsService = newService();
    }

    @Test
    void getReturnsDefaultOverviewWhenUnset() {
        DashboardLayout layout = settingsService.getDashboardLayout();
        assertEquals(DashboardLayouts.TEMPLATE_OVERVIEW, layout.getTemplateId());
        assertFalse(layout.getPanels().isEmpty());
        assertTrue(layout.getPanels().stream().anyMatch(p -> "kpi".equals(p.getId())));
        assertTrue(layout.getPanels().stream().anyMatch(p -> "period-summary".equals(p.getType())));
    }

    @Test
    void putThenGetReturnsSavedLayout() {
        DashboardLayout custom = new DashboardLayout();
        custom.setTemplateId("custom");
        DashboardPanel panel = new DashboardPanel();
        panel.setId("kpi");
        panel.setType("kpi");
        panel.setX(1);
        panel.setY(2);
        panel.setW(3);
        panel.setH(1);
        panel.setVisible(false);
        custom.setPanels(List.of(panel));

        DashboardLayout saved = settingsService.updateDashboardLayout(custom);
        assertEquals("custom", saved.getTemplateId());
        assertEquals(1, saved.getPanels().size());
        assertEquals(1, saved.getPanels().get(0).getX());
        assertFalse(saved.getPanels().get(0).isVisible());

        DashboardLayout loaded = settingsService.getDashboardLayout();
        assertEquals("custom", loaded.getTemplateId());
        assertEquals("kpi", loaded.getPanels().get(0).getId());
        assertEquals(2, loaded.getPanels().get(0).getY());
    }

    @Test
    void layoutSurvivesServiceRestart() {
        DashboardLayout custom = new DashboardLayout();
        custom.setTemplateId(DashboardLayouts.TEMPLATE_MINIMAL);
        DashboardPanel panel = new DashboardPanel();
        panel.setId("summary");
        panel.setType("period-summary");
        panel.setX(0);
        panel.setY(0);
        panel.setW(4);
        panel.setH(1);
        panel.setVisible(true);
        custom.setPanels(List.of(panel));
        settingsService.updateDashboardLayout(custom);

        InsightRuntimeSettingsService restarted = newService();
        DashboardLayout loaded = restarted.getDashboardLayout();
        assertEquals(DashboardLayouts.TEMPLATE_MINIMAL, loaded.getTemplateId());
        assertEquals(1, loaded.getPanels().size());
        assertEquals("summary", loaded.getPanels().get(0).getId());
    }

    @Test
    void templatesContainFivePresets() {
        List<Map<String, Object>> templates = DashboardLayouts.templates();
        assertEquals(5, templates.size());
        assertEquals("overview", templates.get(0).get("id"));
        assertEquals("全局概览", templates.get(0).get("name"));
        assertEquals("troubleshoot", templates.get(1).get("id"));
        assertEquals("jdbc", templates.get(2).get("id"));
        assertEquals("redis", templates.get(3).get("id"));
        assertEquals("minimal", templates.get(4).get("id"));
        assertEquals("极简", templates.get(4).get("name"));
        for (Map<String, Object> t : templates) {
            assertTrue(t.get("layout") instanceof DashboardLayout);
            DashboardLayout layout = (DashboardLayout) t.get("layout");
            assertFalse(layout.getPanels().isEmpty());
        }
    }

    @Test
    void rejectEmptyPanels() {
        DashboardLayout empty = new DashboardLayout();
        empty.setPanels(List.of());
        assertThrows(IllegalArgumentException.class, () -> settingsService.updateDashboardLayout(empty));
    }

    @Test
    void rejectDuplicateIds() {
        DashboardLayout layout = new DashboardLayout();
        DashboardPanel a = new DashboardPanel();
        a.setId("kpi");
        a.setType("kpi");
        DashboardPanel b = new DashboardPanel();
        b.setId("kpi");
        b.setType("kpi");
        layout.setPanels(List.of(a, b));
        assertThrows(IllegalArgumentException.class, () -> settingsService.updateDashboardLayout(layout));
    }

    @Test
    void settingsPutDoesNotDropLayout() {
        DashboardLayout custom = new DashboardLayout();
        custom.setTemplateId("keep-me");
        DashboardPanel panel = new DashboardPanel();
        panel.setId("kpi");
        panel.setType("kpi");
        panel.setW(4);
        panel.setH(1);
        custom.setPanels(List.of(panel));
        settingsService.updateDashboardLayout(custom);

        var patch = settingsService.snapshot();
        patch.getAi().setModel("gpt-test");
        settingsService.updateAndApply(patch);

        assertEquals("keep-me", settingsService.getDashboardLayout().getTemplateId());
        assertEquals("gpt-test", settingsService.effectiveAi().getModel());
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
