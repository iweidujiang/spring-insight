package io.github.iweidujiang.springinsight.storage.impl;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 进程内 List 环形缓冲实现（memory / file 的共用内核）。
 *
 * @since 2026-09-13
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public class InMemorySpanStore implements SpanStore {

    private final InsightServerStorageProperties properties;
    private final String modeLabel;
    private final Object lock = new Object();
    private final List<TraceSpan> spans = new ArrayList<>();

    /**
     * @param properties 存储配置（maxSpans 等）
     * @param modeLabel  对外 mode 名
     */
    public InMemorySpanStore(InsightServerStorageProperties properties, String modeLabel) {
        this.properties = properties;
        this.modeLabel = modeLabel != null ? modeLabel : "memory";
    }

    @Override
    public String mode() {
        return modeLabel;
    }

    @Override
    public int saveAll(List<TraceSpan> batch) {
        if (batch == null || batch.isEmpty()) {
            return 0;
        }
        int added = 0;
        synchronized (lock) {
            for (TraceSpan span : batch) {
                if (span == null || span.getTraceId() == null || span.getSpanId() == null) {
                    continue;
                }
                spans.add(TraceSpan.snapshot(span));
                added++;
            }
            evictByCount();
        }
        return added;
    }

    @Override
    public List<TraceSpan> findByTraceId(String traceId) {
        synchronized (lock) {
            return spans.stream()
                    .filter(s -> traceId.equals(s.getTraceId()))
                    .sorted(Comparator.comparing(s -> n(s.getStartTime())))
                    .map(TraceSpan::snapshot)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public List<TraceSpan> findRecent(int lastHours, int limit) {
        long since = sinceEpochMillis(lastHours);
        int max = Math.max(1, limit);
        synchronized (lock) {
            return spans.stream()
                    .filter(s -> n(s.getStartTime()) >= since)
                    .sorted(Comparator.comparing((TraceSpan s) -> n(s.getStartTime())).reversed())
                    .limit(max)
                    .map(TraceSpan::snapshot)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public List<TraceSpan> findByService(String serviceName, int limit) {
        int max = Math.max(1, limit);
        synchronized (lock) {
            return spans.stream()
                    .filter(s -> serviceName.equals(s.getServiceName()))
                    .sorted(Comparator.comparing((TraceSpan s) -> n(s.getStartTime())).reversed())
                    .limit(max)
                    .map(TraceSpan::snapshot)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public List<TraceSpan> snapshot() {
        synchronized (lock) {
            return spans.stream().map(TraceSpan::snapshot).collect(Collectors.toList());
        }
    }

    /**
     * 用给定列表替换内存内容（file 启动加载用）。
     *
     * @param loaded 已校验的 Span
     */
    public void replaceAll(List<TraceSpan> loaded) {
        synchronized (lock) {
            spans.clear();
            if (loaded != null) {
                for (TraceSpan s : loaded) {
                    if (s == null || s.getTraceId() == null || s.getSpanId() == null) {
                        continue;
                    }
                    spans.add(TraceSpan.snapshot(s));
                }
            }
            evictByCount();
        }
    }

    @Override
    public int size() {
        synchronized (lock) {
            return spans.size();
        }
    }

    @Override
    public int purgeOlderThan(long cutoffEpochMs) {
        synchronized (lock) {
            int before = spans.size();
            spans.removeIf(s -> n(s.getStartTime()) < cutoffEpochMs);
            return before - spans.size();
        }
    }

    private void evictByCount() {
        int max = Math.max(1, properties.getMaxSpans());
        while (spans.size() > max) {
            spans.removeFirst();
        }
    }

    private static long sinceEpochMillis(int lastHours) {
        if (lastHours <= 0) {
            return 0L;
        }
        return Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
    }

    private static long n(Long v) {
        return v != null ? v : 0L;
    }
}
