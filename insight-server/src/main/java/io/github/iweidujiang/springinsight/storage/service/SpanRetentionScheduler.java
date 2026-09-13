package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 按时间窗口裁剪过期 Span（{@code retention.max-age-hours > 0} 时启用）。
 *
 * @since 2026-09-13
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpanRetentionScheduler {

    private final SpanStore spanStore;
    private final InsightServerStorageProperties properties;

    private ScheduledExecutorService scheduler;

    /**
     * 启动周期裁剪任务。
     */
    @PostConstruct
    void start() {
        int hours = properties.getRetention() != null ? properties.getRetention().getMaxAgeHours() : 0;
        if (hours <= 0) {
            log.info("[存储] 未启用按时间保留（retention.max-age-hours=0）");
            return;
        }
        long interval = properties.getRetention().getVacuumIntervalMs();
        if (interval < 5_000L) {
            interval = 5_000L;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "insight-span-retention");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::vacuumSafe, interval, interval, TimeUnit.MILLISECONDS);
        log.info("[存储] 已启用按时间保留：maxAgeHours={}，intervalMs={}", hours, interval);
    }

    /**
     * 停止调度。
     */
    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void vacuumSafe() {
        try {
            int hours = properties.getRetention().getMaxAgeHours();
            if (hours <= 0) {
                return;
            }
            long cutoff = Instant.now().minus(hours, ChronoUnit.HOURS).toEpochMilli();
            int removed = spanStore.purgeOlderThan(cutoff);
            if (removed > 0) {
                log.info("[存储] 时间裁剪删除 {} 条（早于 {} 小时），当前持有={}",
                        removed, hours, spanStore.size());
            }
        } catch (Exception e) {
            log.warn("[存储] 时间裁剪失败: {}", e.getMessage());
        }
    }
}
