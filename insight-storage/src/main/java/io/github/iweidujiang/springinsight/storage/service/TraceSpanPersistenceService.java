package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ┌───────────────────────────────────────────────┐
 * │ 📦 链路追踪内存存储（进程内环形缓冲，无 JDBC）
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────┘
 */
@Slf4j
@Service
public class TraceSpanPersistenceService {

    private static final int DEFAULT_MAX_SPANS = 50_000;

    private final Object lock = new Object();
    private final List<TraceSpan> spans = new ArrayList<>();

    public void saveTraceSpan(TraceSpan span) {
        if (span == null) {
            return;
        }
        saveTraceSpans(List.of(span));
    }

    public void saveTraceSpans(List<TraceSpan> batch) {
        if (batch == null || batch.isEmpty()) {
            log.debug("[内存存储] Span 列表为空，跳过");
            return;
        }

        StopWatch sw = new StopWatch();
        sw.start();
        int added;
        synchronized (lock) {
            for (TraceSpan span : batch) {
                if (span == null || span.getTraceId() == null || span.getSpanId() == null) {
                    continue;
                }
                spans.add(TraceSpan.snapshot(span));
            }
            added = batch.size();
            evictIfNeeded();
        }
        sw.stop();
        log.info("[内存存储] 写入 {} 条 Span，耗时={}ms，当前持有={}", added, sw.getTotalTimeMillis(), size());
    }

    private void evictIfNeeded() {
        while (spans.size() > DEFAULT_MAX_SPANS) {
            spans.remove(0);
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

    private static boolean isError(TraceSpan s) {
        String sc = s.getStatusCode();
        if (sc != null && "ERROR".equalsIgnoreCase(sc)) {
            return true;
        }
        return Boolean.FALSE.equals(s.getSuccess());
    }

    private static long n(Long v) {
        return v != null ? v : 0L;
    }
}
