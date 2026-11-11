package io.github.iweidujiang.springinsight.server.alert;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警历史只读 API（触发 / 恢复 / 升级）。
 *
 * @author 苏渡苇
 * @since 2026-11-10
 */
@RestController
@RequestMapping("/api/v1/ui/alert/history")
@Tag(name = "AlertHistory", description = "告警历史")
public class AlertHistoryController {

    private final AlertHistoryStore historyStore;

    /**
     * @param historyStore 环形历史
     */
    public AlertHistoryController(AlertHistoryStore historyStore) {
        this.historyStore = historyStore;
    }

    /**
     * 最近告警事件列表。
     *
     * @param limit 条数，默认 50
     * @return {@code schemaVersion} + {@code items}
     */
    @GetMapping
    @Operation(summary = "最近告警历史")
    public Map<String, Object> recent(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        List<Map<String, Object>> items = historyStore.recent(limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", 1);
        body.put("items", items);
        return body;
    }
}
