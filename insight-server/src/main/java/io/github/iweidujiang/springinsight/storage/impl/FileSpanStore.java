package io.github.iweidujiang.springinsight.storage.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * file 模式：内存查询 + JSON 防抖落盘。
 *
 * @since 2026-09-13
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
public class FileSpanStore implements SpanStore {

    private final InsightServerStorageProperties properties;
    private final ObjectMapper objectMapper;
    private final InMemorySpanStore memory;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final ScheduledExecutorService flushScheduler;
    private ScheduledFuture<?> pendingFlush;

    /**
     * @param properties   存储配置
     * @param objectMapper JSON
     */
    public FileSpanStore(InsightServerStorageProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.memory = new InMemorySpanStore(properties, "file");
        loadFromFile();
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "insight-span-flush");
            t.setDaemon(true);
            return t;
        });
        log.info("[存储] 模式=file，路径={}，maxSpans={}，已加载 {} 条",
                properties.getFilePath(), properties.getMaxSpans(), memory.size());
    }

    @Override
    public String mode() {
        return "file";
    }

    @Override
    public int saveAll(List<TraceSpan> spans) {
        int added = memory.saveAll(spans);
        if (added > 0) {
            scheduleFlush();
        }
        return added;
    }

    @Override
    public List<TraceSpan> findByTraceId(String traceId) {
        return memory.findByTraceId(traceId);
    }

    @Override
    public List<TraceSpan> findRecent(int lastHours, int limit) {
        return memory.findRecent(lastHours, limit);
    }

    @Override
    public List<TraceSpan> findByService(String serviceName, int limit) {
        return memory.findByService(serviceName, limit);
    }

    @Override
    public List<TraceSpan> snapshot() {
        return memory.snapshot();
    }

    @Override
    public int size() {
        return memory.size();
    }

    @Override
    public long evictedCount() {
        return memory.evictedCount();
    }

    @Override
    public int purgeOlderThan(long cutoffEpochMs) {
        int removed = memory.purgeOlderThan(cutoffEpochMs);
        if (removed > 0) {
            scheduleFlush();
        }
        return removed;
    }

    @Override
    public void close() {
        cancelPendingFlush();
        flushToFileNow(true);
        flushScheduler.shutdown();
        try {
            flushScheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void scheduleFlush() {
        dirty.set(true);
        synchronized (this) {
            if (pendingFlush != null && !pendingFlush.isDone()) {
                pendingFlush.cancel(false);
            }
            long delay = Math.max(200L, properties.getFlushDelayMs());
            pendingFlush = flushScheduler.schedule(() -> flushToFileNow(false), delay, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelPendingFlush() {
        synchronized (this) {
            if (pendingFlush != null) {
                pendingFlush.cancel(false);
                pendingFlush = null;
            }
        }
    }

    private void loadFromFile() {
        Path path = Path.of(properties.getFilePath()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            log.info("[存储] 持久化文件不存在，将在首次写入时创建: {}", path);
            return;
        }
        try {
            List<TraceSpan> loaded = objectMapper.readValue(path.toFile(), new TypeReference<>() {});
            memory.replaceAll(loaded);
            log.info("[存储] 已从文件加载 {} 条 Span ← {}", memory.size(), path);
        } catch (IOException e) {
            log.error("[存储] 读取持久化文件失败: {} — {}", path, e.getMessage());
        }
    }

    private void flushToFileNow(boolean force) {
        if (!force && !dirty.get()) {
            return;
        }
        Path path = Path.of(properties.getFilePath()).toAbsolutePath().normalize();
        List<TraceSpan> snapshot = memory.snapshot();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), snapshot);
            try {
                Files.move(tmp, path,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            dirty.set(false);
            log.info("[存储] 已刷盘 {} 条 Span → {}", snapshot.size(), path);
        } catch (IOException e) {
            dirty.set(true);
            log.error("[存储] 刷盘失败: {} — {}", path, e.getMessage());
        }
    }
}
