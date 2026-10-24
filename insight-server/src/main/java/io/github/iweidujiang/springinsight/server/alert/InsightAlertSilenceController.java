package io.github.iweidujiang.springinsight.server.alert;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 细粒度告警静默规则 API。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ui/alert/silences")
@RequiredArgsConstructor
@Tag(name = "AlertSilence", description = "告警静默规则")
public class InsightAlertSilenceController {

    private final InsightAlertSilenceService silenceService;

    /**
     * 列出全部静默规则。
     *
     * @return items
     */
    @GetMapping
    @Operation(summary = "列出静默规则")
    public Map<String, Object> list() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (SilenceRule rule : silenceService.list()) {
            items.add(InsightAlertSilenceService.toMap(rule));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", 1);
        body.put("items", items);
        return body;
    }

    /**
     * 创建静默规则。
     *
     * @param body 请求体
     * @return 创建结果
     */
    @PostMapping
    @Operation(summary = "创建静默规则")
    public ResponseEntity<?> create(@RequestBody SilenceRule body) {
        try {
            SilenceRule created = silenceService.create(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(InsightAlertSilenceService.toMap(created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.warn("[告警静默] 创建失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("message", "创建失败"));
        }
    }

    /**
     * 删除静默规则。
     *
     * @param id 规则 id
     * @return 204 / 404
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除静默规则")
    public ResponseEntity<?> delete(@PathVariable("id") String id) {
        if (!silenceService.delete(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
