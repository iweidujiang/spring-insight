package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 链路 Span 门面：委托 {@link SpanStore} 读写，并在内存快照上做聚合查询。
 *
 * @since 2026-09-13
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceSpanPersistenceService {

    private final SpanStore spanStore;

    /**
     * 写入单条 Span。
     *
     * @param span 可为空（忽略）
     */
    public void saveTraceSpan(TraceSpan span) {
        if (span == null) {
            return;
        }
        saveTraceSpans(List.of(span));
    }

    /**
     * 批量写入 Span。
     *
     * @param batch 批次
     */
    public void saveTraceSpans(List<TraceSpan> batch) {
        if (batch == null || batch.isEmpty()) {
            log.debug("[存储] Span 列表为空，跳过");
            return;
        }
        StopWatch sw = new StopWatch();
        sw.start();
        int added = spanStore.saveAll(batch);
        sw.stop();
        log.info("[存储] 写入 {} 条 Span（mode={}），耗时={}ms，当前持有={}",
                added, spanStore.mode(), sw.getTotalTimeMillis(), spanStore.size());
    }

    /**
     * @param traceId Trace ID
     * @return 该 Trace 的 Span 列表
     */
    public List<TraceSpan> getTraceById(String traceId) {
        return spanStore.findByTraceId(traceId);
    }

    /**
     * @param lastHours 时间窗口
     * @param limit     条数
     * @return 最近 Span
     */
    public List<TraceSpan> getRecentSpans(int lastHours, int limit) {
        return spanStore.findRecent(lastHours, limit);
    }

    /**
     * 按 Trace ID 聚合的最近链路摘要（一行 = 一次请求）。
     *
     * @param lastHours     时间窗口（小时）
     * @param limit         最多返回几条 Trace
     * @param serviceName   可选服务过滤
     * @param status        all|error|ok
     * @param query         可选模糊匹配
     * @param minDurationMs 耗时下限
     * @return 摘要行
     */
    public List<Map<String, Object>> getRecentTraceSummaries(
            int lastHours, int limit, String serviceName, String status, String query, long minDurationMs) {
        long sinceTime = sinceEpochMillis(lastHours);
        String svc = serviceName != null ? serviceName.trim() : "";
        String st = status != null ? status.trim().toLowerCase() : "all";
        String q = query != null ? query.trim().toLowerCase() : "";
        long minDur = Math.max(0L, minDurationMs);
        int max = Math.max(1, limit);

        record Acc(
                String traceId,
                long minStart,
                long maxEnd,
                int spanCount,
                boolean hasError,
                String rootService,
                String rootOperation,
                java.util.LinkedHashSet<String> services
        ) {}

        Map<String, Acc> byTrace = new LinkedHashMap<>();
        for (TraceSpan s : spanStore.snapshot()) {
            if (s == null || s.getTraceId() == null || s.getTraceId().isBlank()) {
                continue;
            }
            long start = n(s.getStartTime());
            if (start < sinceTime) {
                continue;
            }
            long end = n(s.getEndTime());
            if (end <= 0) {
                end = start + n(s.getDurationMs());
            }
            Acc acc = byTrace.get(s.getTraceId());
            if (acc == null) {
                java.util.LinkedHashSet<String> services = new java.util.LinkedHashSet<>();
                if (s.getServiceName() != null && !s.getServiceName().isBlank()) {
                    services.add(s.getServiceName());
                }
                boolean root = s.getParentSpanId() == null || s.getParentSpanId().isBlank();
                byTrace.put(s.getTraceId(), new Acc(
                        s.getTraceId(),
                        start,
                        Math.max(start, end),
                        1,
                        isError(s),
                        root ? s.getServiceName() : null,
                        root ? s.getOperationName() : null,
                        services
                ));
            } else {
                boolean root = s.getParentSpanId() == null || s.getParentSpanId().isBlank();
                if (s.getServiceName() != null && !s.getServiceName().isBlank()) {
                    acc.services().add(s.getServiceName());
                }
                byTrace.put(s.getTraceId(), new Acc(
                        acc.traceId(),
                        Math.min(acc.minStart(), start),
                        Math.max(acc.maxEnd(), Math.max(start, end)),
                        acc.spanCount() + 1,
                        acc.hasError() || isError(s),
                        root && (acc.rootService() == null || acc.rootService().isBlank())
                                ? s.getServiceName() : acc.rootService(),
                        root && (acc.rootOperation() == null || acc.rootOperation().isBlank())
                                ? s.getOperationName() : acc.rootOperation(),
                        acc.services()
                ));
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Acc acc : byTrace.values()) {
            if (!svc.isEmpty() && !acc.services().contains(svc)) {
                continue;
            }
            if ("error".equals(st) && !acc.hasError()) {
                continue;
            }
            if ("ok".equals(st) && acc.hasError()) {
                continue;
            }
            String rootOp = acc.rootOperation() != null ? acc.rootOperation() : "";
            String rootSvc = acc.rootService() != null ? acc.rootService() : "";
            if (rootSvc.isBlank() && !acc.services().isEmpty()) {
                rootSvc = acc.services().iterator().next();
            }
            if (!q.isEmpty()) {
                boolean match = acc.traceId().toLowerCase().contains(q)
                        || rootOp.toLowerCase().contains(q)
                        || rootSvc.toLowerCase().contains(q)
                        || acc.services().stream().anyMatch(x -> x.toLowerCase().contains(q));
                if (!match) {
                    continue;
                }
            }
            long durationMs = Math.max(0L, acc.maxEnd() - acc.minStart());
            if (durationMs < minDur) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("traceId", acc.traceId());
            row.put("serviceName", rootSvc);
            row.put("operationName", rootOp.isBlank() ? "(unknown)" : rootOp);
            row.put("startTime", acc.minStart());
            row.put("durationMs", durationMs);
            row.put("spanCount", acc.spanCount());
            row.put("serviceCount", acc.services().size());
            row.put("hasError", acc.hasError());
            row.put("statusCode", acc.hasError() ? "ERROR" : "OK");
            out.add(row);
        }
        out.sort((a, b) -> Long.compare(
                ((Number) b.get("startTime")).longValue(),
                ((Number) a.get("startTime")).longValue()));
        if (out.size() > max) {
            return out.subList(0, max);
        }
        return out;
    }

    /**
     * @return 去重后的服务名列表
     */
    public List<String> getAllServiceNames() {
        return spanStore.snapshot().stream()
                .map(TraceSpan::getServiceName)
                .filter(Objects::nonNull)
                .filter(n -> !n.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * @param lastHours 时间窗口
     * @return 依赖边汇总
     */
    public List<Map<String, Object>> getServiceDependencies(int lastHours) {
        long sinceTime = sinceEpochMillis(lastHours);
        record Key(String src, String tgt) {}
        Map<Key, long[]> agg = new HashMap<>();
        for (TraceSpan s : spanStore.snapshot()) {
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

    /**
     * @return 全量按服务计数
     */
    public List<Map<String, Object>> getSpanCountByService() {
        return getSpanCountByService(0);
    }

    /**
     * @param lastHours 时间窗口；{@code <=0} 不限
     * @return 按服务 Span 数
     */
    public List<Map<String, Object>> getSpanCountByService(int lastHours) {
        long sinceTime = sinceEpochMillis(lastHours);
        Map<String, Long> counts = new HashMap<>();
        for (TraceSpan s : spanStore.snapshot()) {
            if (n(s.getStartTime()) < sinceTime) {
                continue;
            }
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

    /**
     * @param lastHours 时间窗口
     * @return 有错误的服务分析
     */
    public List<Map<String, Object>> findHighErrorServices(int lastHours) {
        long sinceTime = sinceEpochMillis(lastHours);
        Map<String, long[]> agg = new HashMap<>();
        for (TraceSpan s : spanStore.snapshot()) {
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

    /**
     * @param lastHours 时间窗口
     * @param limit     返回上限
     * @return 延迟摘要
     */
    public List<Map<String, Object>> getServiceLatencySummaries(int lastHours, int limit) {
        long sinceTime = sinceEpochMillis(lastHours);
        int max = Math.max(1, limit);

        record Acc(List<Long> durations, long errorCount) {}
        Map<String, Acc> byService = new HashMap<>();

        for (TraceSpan s : spanStore.snapshot()) {
            if (s == null || n(s.getStartTime()) < sinceTime) {
                continue;
            }
            String name = s.getServiceName();
            if (name == null || name.isBlank()) {
                continue;
            }
            Acc acc = byService.computeIfAbsent(name, x -> new Acc(new ArrayList<>(), 0L));
            long dur = n(s.getDurationMs());
            if (dur <= 0 && s.getEndTime() != null) {
                dur = Math.max(0L, n(s.getEndTime()) - n(s.getStartTime()));
            }
            acc.durations().add(Math.max(0L, dur));
            if (isError(s)) {
                byService.put(name, new Acc(acc.durations(), acc.errorCount() + 1));
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Acc> e : byService.entrySet()) {
            Acc acc = e.getValue();
            List<Long> durs = acc.durations();
            if (durs.isEmpty()) {
                continue;
            }
            durs.sort(Long::compareTo);
            int n = durs.size();
            long sum = 0L;
            for (Long d : durs) {
                sum += d;
            }
            long p50 = percentile(durs, 0.50);
            long p95 = percentile(durs, 0.95);
            long maxMs = durs.get(n - 1);
            double avg = sum / (double) n;
            double errRate = Math.round((acc.errorCount() * 10000.0 / n)) / 100.0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("service_name", e.getKey());
            row.put("span_count", n);
            row.put("error_count", acc.errorCount());
            row.put("error_rate", errRate);
            row.put("avg_ms", Math.round(avg * 100.0) / 100.0);
            row.put("p50_ms", p50);
            row.put("p95_ms", p95);
            row.put("max_ms", maxMs);
            out.add(row);
        }
        out.sort((a, b) -> Long.compare(
                ((Number) b.get("p95_ms")).longValue(),
                ((Number) a.get("p95_ms")).longValue()));
        if (out.size() > max) {
            return new ArrayList<>(out.subList(0, max));
        }
        return out;
    }

    /**
     * @param serviceName 服务名
     * @param limit       条数
     * @return 该服务最近 Span
     */
    public List<TraceSpan> getRecentSpansByService(String serviceName, int limit) {
        return spanStore.findByService(serviceName, limit);
    }

    /**
     * @return 当前持有条数
     */
    public int getStoredSpanCount() {
        return spanStore.size();
    }

    /**
     * @return 当前存储模式
     */
    public String getStorageMode() {
        return spanStore.mode();
    }

    private static long percentile(List<Long> sortedAsc, double p) {
        if (sortedAsc.isEmpty()) {
            return 0L;
        }
        if (sortedAsc.size() == 1) {
            return sortedAsc.getFirst();
        }
        double idx = p * (sortedAsc.size() - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return sortedAsc.get(lo);
        }
        double w = idx - lo;
        return Math.round(sortedAsc.get(lo) * (1 - w) + sortedAsc.get(hi) * w);
    }

    private static boolean isError(TraceSpan s) {
        String sc = s.getStatusCode();
        if ("ERROR".equalsIgnoreCase(sc)) {
            return true;
        }
        return Boolean.FALSE.equals(s.getSuccess());
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
