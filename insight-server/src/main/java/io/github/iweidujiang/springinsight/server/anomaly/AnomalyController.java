package io.github.iweidujiang.springinsight.server.anomaly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 无阈值异常检测只读 API（供控制台标记，不替代阈值告警）。
 *
 * @author 苏渡苇
 * @since 2026-10-23
 */
@RestController
@RequestMapping("/api/v1/ui/anomalies")
@RequiredArgsConstructor
@Tag(name = "Anomaly", description = "基础异常检测")
public class AnomalyController {

    private final AnomalyDetector anomalyDetector;

    /**
     * 最近检测到的异常列表。
     *
     * @param limit 条数，默认 20
     * @return {@code items} 列表
     */
    @GetMapping
    @Operation(summary = "最近异常")
    public Map<String, Object> recent(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        List<Map<String, Object>> items = anomalyDetector.recentAnomalies(limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", 1);
        body.put("items", items);
        return body;
    }
}
