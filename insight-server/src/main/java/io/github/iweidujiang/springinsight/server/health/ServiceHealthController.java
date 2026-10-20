package io.github.iweidujiang.springinsight.server.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务健康评分 API。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ui/services")
@RequiredArgsConstructor
@Tag(name = "ServiceHealth", description = "服务健康评分")
public class ServiceHealthController {

    private final HealthScoreService healthScoreService;

    /**
     * 时间窗内各服务健康分列表。
     *
     * @param hours 时间窗小时；默认 1。小于等于 0 表示不设下界
     * @return 服务健康分数组
     */
    @GetMapping("/health")
    @Operation(summary = "服务健康评分")
    public ResponseEntity<?> health(
            @RequestParam(value = "hours", defaultValue = "1") int hours) {
        try {
            List<Map<String, Object>> rows = healthScoreService.listServiceHealth(hours);
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.error("获取服务健康评分失败", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "获取健康评分失败"));
        }
    }
}
