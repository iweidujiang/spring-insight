package io.github.iweidujiang.springinsight.server.slo;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SLO 配置与错误预算 API。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ui/slo")
@RequiredArgsConstructor
@Tag(name = "SLO", description = "服务 SLO 与错误预算")
public class InsightSloController {

    private final InsightSloService sloService;

    /**
     * 列出全部 SLO 配置。
     *
     * @return items
     */
    @GetMapping
    @Operation(summary = "列出 SLO")
    public Map<String, Object> list() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ServiceSlo slo : sloService.list()) {
            items.add(InsightSloService.toMap(slo));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", 1);
        body.put("items", items);
        return body;
    }

    /**
     * 全部已配置服务的错误预算（仪表盘汇总）。
     *
     * @param hours 统计窗口小时；缺省 0 表示各用自身 windowHours
     * @return items
     */
    @GetMapping("/budgets")
    @Operation(summary = "列出错误预算")
    public Map<String, Object> listBudgets(
            @RequestParam(value = "hours", defaultValue = "0") long hours) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", 1);
        body.put("items", sloService.listBudgets(hours));
        return body;
    }

    /**
     * 创建 SLO。
     *
     * @param body 请求体
     * @return 创建结果
     */
    @PostMapping
    @Operation(summary = "创建 SLO")
    public ResponseEntity<?> create(@RequestBody ServiceSlo body) {
        try {
            ServiceSlo created = sloService.create(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(InsightSloService.toMap(created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 更新 SLO。
     *
     * @param service 服务名
     * @param body    请求体
     * @return 更新结果
     */
    @PutMapping("/{service}")
    @Operation(summary = "更新 SLO")
    public ResponseEntity<?> update(@PathVariable("service") String service,
                                    @RequestBody ServiceSlo body) {
        try {
            ServiceSlo updated = sloService.update(service, body);
            return ResponseEntity.ok(InsightSloService.toMap(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.startsWith("SLO 不存在")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", msg));
        }
    }

    /**
     * 删除 SLO。
     *
     * @param service 服务名
     * @return 204 / 404
     */
    @DeleteMapping("/{service}")
    @Operation(summary = "删除 SLO")
    public ResponseEntity<?> delete(@PathVariable("service") String service) {
        if (!sloService.delete(service)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 单服务错误预算。
     *
     * @param service 服务名
     * @param hours   统计窗口；缺省用 SLO windowHours
     * @return 预算体；无配置 404
     */
    @GetMapping("/{service}/budget")
    @Operation(summary = "错误预算")
    public ResponseEntity<?> budget(@PathVariable("service") String service,
                                    @RequestParam(value = "hours", required = false) Long hours) {
        long h = hours == null ? 0L : hours;
        return sloService.budget(service, h)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
