package io.github.iweidujiang.springinsight.server.storage;

import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 控制台存储摘要与清除历史 Span。
 *
 * @since 2026-09-20
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@RestController
@RequestMapping("/api/v1/ui/storage")
@RequiredArgsConstructor
public class InsightStorageController {

    private final TraceSpanPersistenceService persistenceService;
    private final InsightServerStorageProperties storageProperties;

    /**
     * @return 容量摘要
     */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", persistenceService.getStorageMode());
        body.put("stored", persistenceService.getStoredSpanCount());
        body.put("max", storageProperties.getMaxSpans());
        body.put("evicted", persistenceService.getEvictedSpanCount());
        return body;
    }

    /**
     * 清除历史：all / older_than / service。
     *
     * @param request 请求体
     * @return 删除结果；参数非法时 400
     */
    @PostMapping("/clear")
    public ResponseEntity<?> clear(@RequestBody ClearRequest request) {
        if (request == null || !StringUtils.hasText(request.scope())) {
            return ResponseEntity.badRequest().body(Map.of("message", "scope 必填：all | older_than | service"));
        }
        String scope = request.scope().trim().toLowerCase(Locale.ROOT);
        int deleted;
        try {
            deleted = switch (scope) {
                case "all" -> persistenceService.clearAllSpans();
                case "older_than" -> purgeOlder(request);
                case "service" -> purgeService(request);
                default -> throw new IllegalArgumentException("不支持的 scope: " + request.scope());
            };
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", deleted);
        body.put("remaining", persistenceService.getStoredSpanCount());
        body.put("mode", persistenceService.getStorageMode());
        body.put("scope", scope);
        return ResponseEntity.ok(body);
    }

    private int purgeOlder(ClearRequest request) {
        long cutoff;
        if (request.cutoffEpochMs() != null && request.cutoffEpochMs() > 0) {
            cutoff = request.cutoffEpochMs();
        } else if (request.olderThanHours() != null && request.olderThanHours() > 0) {
            cutoff = Instant.now().minus(request.olderThanHours(), ChronoUnit.HOURS).toEpochMilli();
        } else {
            throw new IllegalArgumentException("older_than 需提供 olderThanHours>0 或 cutoffEpochMs>0");
        }
        return persistenceService.purgeSpansOlderThan(cutoff);
    }

    private int purgeService(ClearRequest request) {
        if (!StringUtils.hasText(request.serviceName())) {
            throw new IllegalArgumentException("service 范围需提供 serviceName");
        }
        return persistenceService.purgeSpansByService(request.serviceName().trim());
    }

    /**
     * 清除请求体。
     *
     * @param scope          all | older_than | service
     * @param olderThanHours 早于 N 小时（与 cutoffEpochMs 二选一）
     * @param cutoffEpochMs  绝对截止毫秒
     * @param serviceName    服务名
     */
    public record ClearRequest(
            String scope,
            Integer olderThanHours,
            Long cutoffEpochMs,
            String serviceName
    ) {
    }
}
