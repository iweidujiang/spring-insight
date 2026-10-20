package io.github.iweidujiang.springinsight.server.dashboard;

import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘布局读写与预设模板。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ui/dashboard")
@RequiredArgsConstructor
@Tag(name = "DashboardLayout", description = "仪表盘布局")
public class DashboardLayoutController {

    private final InsightRuntimeSettingsService settingsService;

    /**
     * @return 当前布局；未保存过时为默认全局概览
     */
    @GetMapping("/layout")
    @Operation(summary = "获取仪表盘布局")
    public DashboardLayout getLayout() {
        return settingsService.getDashboardLayout();
    }

    /**
     * 保存布局到 runtime-settings.json。
     *
     * @param body 布局 JSON
     * @return 保存后的布局
     */
    @PutMapping("/layout")
    @Operation(summary = "保存仪表盘布局")
    public ResponseEntity<?> putLayout(@RequestBody DashboardLayout body) {
        try {
            return ResponseEntity.ok(settingsService.updateDashboardLayout(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.warn("[仪表盘布局] 保存失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("message", "保存失败"));
        }
    }

    /**
     * @return 预设模板列表（含各模板 layout）
     */
    @GetMapping("/templates")
    @Operation(summary = "仪表盘预设模板")
    public List<Map<String, Object>> templates() {
        return DashboardLayouts.templates();
    }
}
