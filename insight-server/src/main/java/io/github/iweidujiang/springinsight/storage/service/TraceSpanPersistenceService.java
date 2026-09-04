package io.github.iweidujiang.springinsight.storage.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 链路 Span 存储：默认进程内内存；可选 JSON 文件持久化（重启可恢复）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceSpanPersistenceService {

    private final InsightServerStorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    private final Object lock = new Object();
    private final List<TraceSpan> spans = new ArrayList<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    private ScheduledExecutorService flushScheduler;
    private ScheduledFuture<?> pendingFlush;

    @PostConstruct
    void init() {
        if (storageProperties.isFileMode()) {
            loadFromFile();
            flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "insight-span-flush");
                t.setDaemon(true);
                return t;
            });
            log.info("[存储] 模式=file，路径={}，maxSpans={}，已加载 {} 条",
                    storageProperties.getFilePath(), storageProperties.getMaxSpans(), size());
        } else {
            log.info("[存储] 模式=memory，maxSpans={}（重启将清空）", storageProperties.getMaxSpans());
        }
    }

    @PreDestroy
    void shutdown() {
        if (!storageProperties.isFileMode()) {
            return;
        }
        cancelPendingFlush();
        flushToFileNow(true);
        if (flushScheduler != null) {
            flushScheduler.shutdown();
            try {
                flushScheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void saveTraceSpan(TraceSpan span) {
        if (span == null) {
            return;
        }
        saveTraceSpans(List.of(span));
    }

    public void saveTraceSpans(List<TraceSpan> batch) {
        if (batch == null || batch.isEmpty()) {
            log.debug("[存储] Span 列表为空，跳过");
            return;
        }

        StopWatch sw = new StopWatch();
        sw.start();
        int added = 0;
        synchronized (lock) {
            for (TraceSpan span : batch) {
                if (span == null || span.getTraceId() == null || span.getSpanId() == null) {
                    continue;
                }
                spans.add(TraceSpan.snapshot(span));
                added++;
            }
            evictIfNeeded();
        }
        sw.stop();
        log.info("[存储] 写入 {} 条 Span，耗时={}ms，当前持有={}", added, sw.getTotalTimeMillis(), size());
        if (storageProperties.isFileMode() && added > 0) {
            scheduleFlush();
        }
    }

    private void evictIfNeeded() {
        int max = Math.max(1, storageProperties.getMaxSpans());
        while (spans.size() > max) {
            spans.removeFirst();
        }
    }

    private int size() {
        return spans.size();
    }

    public List<TraceSpan> getTraceById(String traceId) {
        synchronized (lock) {
            return spans.stream()
                    .filter(s -> traceId.equals(s.getTraceId()))
                    .sorted(Comparator.comparing(s -> n(s.getStartTime())))
                    .map(TraceSpan::snapshot)
                    .collect(Collectors.toList());
        }
    }

    public List<TraceSpan> getRecentSpans(int lastHours, int limit) {
        long sinceTime = Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
        synchronized (lock) {
            return spans.stream()
                    .filter(s -> n(s.getStartTime()) >= sinceTime)
                    .sorted(Comparator.comparing((TraceSpan s) -> n(s.getStartTime())).reversed())
                    .limit(limit)
                    .map(TraceSpan::snapshot)
                    .collect(Collectors.toList());
        }
    }

    public List<String> getAllServiceNames() {
        synchronized (lock) {
            return spans.stream()
                    .map(TraceSpan::getServiceName)
                    .filter(Objects::nonNull)
                    .filter(n -> !n.isBlank())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public List<Map<String, Object>> getServiceDependencies(int lastHours) {
        long sinceTime = Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
        synchronized (lock) {
            record Key(String src, String tgt) {}
            Map<Key, long[]> agg = new HashMap<>();
            for (TraceSpan s : spans) {
                if (n(s.getStartTime()) < sinceTime) {
                    continue;
                }
                String remote = s.getRemoteService();
                if (remote == null || remote.isBlank()) {
                    continue;
                }
                String src = s.getServiceName() != null ? s.getServiceName() : "";
                Key k = new Key(src, remote);
                long[] a = agg.computeIfAbsent(k, x -> new long[]{0L, 0L});
                a[0]++;
                a[1] += n(s.getDurationMs());
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map.Entry<Key, long[]> e : agg.entrySet()) {
                long cnt = e.getValue()[0];
                if (cnt <= 0) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("source_service", e.getKey().src());
                row.put("target_service", e.getKey().tgt());
                row.put("call_count", cnt);
                row.put("avg_duration", (double) e.getValue()[1] / (double) cnt);
                out.add(row);
            }
            return out;
        }
    }

    public List<Map<String, Object>> getSpanCountByService() {
        synchronized (lock) {
            Map<String, Long> counts = new HashMap<>();
            for (TraceSpan s : spans) {
                String name = s.getServiceName();
                if (name == null || name.isBlank()) {
                    continue;
                }
                counts.merge(name, 1L, Long::sum);
            }
            return counts.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .map(e -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("service_name", e.getKey());
                        row.put("span_count", e.getValue());
                        return row;
                    })
                    .collect(Collectors.toList());
        }
    }

    public List<Map<String, Object>> findHighErrorServices(int lastHours) {
        long sinceTime = Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
        synchronized (lock) {
            Map<String, long[]> agg = new HashMap<>();
            for (TraceSpan s : spans) {
                if (n(s.getStartTime()) < sinceTime) {
                    continue;
                }
                String name = s.getServiceName();
                if (name == null || name.isBlank()) {
                    continue;
                }
                long[] a = agg.computeIfAbsent(name, x -> new long[]{0L, 0L});
                a[0]++;
                if (isError(s)) {
                    a[1]++;
                }
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map.Entry<String, long[]> e : agg.entrySet()) {
                long total = e.getValue()[0];
                long err = e.getValue()[1];
                if (err <= 0) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("service_name", e.getKey());
                row.put("total_calls", total);
                row.put("error_calls", err);
                row.put("error_rate", Math.round((err * 10000.0 / total)) / 100.0);
                out.add(row);
            }
            out.sort((a, b) -> Double.compare(
                    ((Number) b.get("error_rate")).doubleValue(),
                    ((Number) a.get("error_rate")).doubleValue()));
            return out;
        }
    }

    public List<TraceSpan> getRecentSpansByService(String serviceName, int limit) {
        synchronized (lock) {
            return spans.stream()
                    .filter(s -> serviceName.equals(s.getServiceName()))
                    .sorted(Comparator.comparing((TraceSpan s) -> n(s.getStartTime())).reversed())
                    .limit(limit)
                    .map(TraceSpan::snapshot)
                    .collect(Collectors.toList());
        }
    }

    /** 当前持有条数（运维/健康检查可用） */
    public int getStoredSpanCount() {
        synchronized (lock) {
            return spans.size();
        }
    }

    private void scheduleFlush() {
        dirty.set(true);
        if (flushScheduler == null) {
            return;
        }
        synchronized (this) {
            if (pendingFlush != null && !pendingFlush.isDone()) {
                pendingFlush.cancel(false);
            }
            long delay = Math.max(200L, storageProperties.getFlushDelayMs());
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
        Path path = Path.of(storageProperties.getFilePath()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            log.info("[存储] 持久化文件不存在，将在首次写入时创建: {}", path);
            return;
        }
        try {
            List<TraceSpan> loaded = objectMapper.readValue(path.toFile(), new TypeReference<List<TraceSpan>>() {});
            if (loaded == null || loaded.isEmpty()) {
                return;
            }
            synchronized (lock) {
                spans.clear();
                for (TraceSpan s : loaded) {
                    if (s == null || s.getTraceId() == null || s.getSpanId() == null) {
                        continue;
                    }
                    spans.add(TraceSpan.snapshot(s));
                }
                evictIfNeeded();
            }
            log.info("[存储] 已从文件加载 {} 条 Span ← {}", size(), path);
        } catch (IOException e) {
            log.error("[存储] 读取持久化文件失败: {} — {}", path, e.getMessage());
        }
    }

    private void flushToFileNow(boolean force) {
        if (!storageProperties.isFileMode()) {
            return;
        }
        if (!force && !dirty.get()) {
            return;
        }
        Path path = Path.of(storageProperties.getFilePath()).toAbsolutePath().normalize();
        List<TraceSpan> snapshot;
        synchronized (lock) {
            snapshot = spans.stream().map(TraceSpan::snapshot).collect(Collectors.toList());
        }
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

    private static boolean isError(TraceSpan s) {
        String sc = s.getStatusCode();
        if ("ERROR".equalsIgnoreCase(sc)) {
            return true;
        }
        return Boolean.FALSE.equals(s.getSuccess());
    }

    private static long n(Long v) {
        return v != null ? v : 0L;
    }
}
