package io.github.iweidujiang.springinsight.server.settings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 控制台运行时设置 API（告警 / AI；保存后立即生效）。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ui/settings")
@RequiredArgsConstructor
public class InsightSettingsController {

    private final InsightRuntimeSettingsService settingsService;

    /**
     * @return 脱敏后的当前设置
     */
    @GetMapping
    public Map<String, Object> get() {
        return settingsService.publicView();
    }

    /**
     * 保存并生效；密钥字段留空表示不修改。
     *
     * @param body 请求体
     * @return 保存后的脱敏视图
     */
    @PutMapping
    public ResponseEntity<?> put(@RequestBody InsightRuntimeSettings body) {
        try {
            return ResponseEntity.ok(settingsService.updateAndApply(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.warn("[设置] 保存失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("message", "保存失败: " + e.getMessage()));
        }
    }
}
