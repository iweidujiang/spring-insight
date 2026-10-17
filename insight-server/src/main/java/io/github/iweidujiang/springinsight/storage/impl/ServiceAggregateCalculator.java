package io.github.iweidujiang.springinsight.storage.impl;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.storage.spi.ServiceAggregate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从已在内存中的 Span 计算服务级聚合，供 memory / file 使用，并作为 SQLite 结果的对照。
 */
public final class ServiceAggregateCalculator {

    private ServiceAggregateCalculator() {
    }

    public static List<ServiceAggregate> fromSpans(List<TraceSpan> spans, long sinceEpoch, String service) {
        String filter = service == null ? "" : service.trim();
        Map<String, List<Long>> durations = new LinkedHashMap<>();
        Map<String, Long> errors = new LinkedHashMap<>();
        if (spans != null) {
            for (TraceSpan span : spans) {
                if (span == null || n(span.getStartTime()) < sinceEpoch) {
                    continue;
                }
                String name = span.getServiceName() == null ? "" : span.getServiceName();
                if (!filter.isEmpty() && !filter.equals(name)) {
                    continue;
                }
                durations.computeIfAbsent(name, key -> new ArrayList<>()).add(durationOf(span));
                if (isError(span)) {
                    errors.merge(name, 1L, Long::sum);
                }
            }
        }
        List<ServiceAggregate> rows = new ArrayList<>();
        for (Map.Entry<String, List<Long>> entry : durations.entrySet()) {
            rows.add(toRow(entry.getKey(), entry.getValue(), errors.getOrDefault(entry.getKey(), 0L)));
        }
        rows.sort(Comparator.comparing(ServiceAggregate::serviceName));
        return rows;
    }

    static ServiceAggregate toRow(String serviceName, List<Long> durations, long errorCount) {
        List<Long> sorted = new ArrayList<>(durations);
        sorted.sort(Long::compareTo);
        long sum = 0L;
        long max = 0L;
        for (Long value : sorted) {
            long dur = value == null ? 0L : value;
            sum += dur;
            max = Math.max(max, dur);
        }
        double avg = sorted.isEmpty() ? 0.0 : Math.round((sum / (double) sorted.size()) * 100.0) / 100.0;
        return new ServiceAggregate(
                serviceName == null ? "" : serviceName,
                sorted.size(),
                errorCount,
                avg,
                max,
                percentile(sorted, 0.50),
                percentile(sorted, 0.95));
    }

    static long durationOf(TraceSpan span) {
        long dur = n(span.getDurationMs());
        if (dur <= 0 && span.getEndTime() != null) {
            dur = Math.max(0L, n(span.getEndTime()) - n(span.getStartTime()));
        }
        return Math.max(0L, dur);
    }

    static boolean isError(TraceSpan span) {
        if (span.getStatusCode() != null && "ERROR".equalsIgnoreCase(span.getStatusCode())) {
            return true;
        }
        return Boolean.FALSE.equals(span.getSuccess());
    }

    /**
     * 与控制台现有分位数相同：线性插值后四舍五入。
     */
    static long percentile(List<Long> sortedAsc, double p) {
        if (sortedAsc.isEmpty()) {
            return 0L;
        }
        if (sortedAsc.size() == 1) {
            return sortedAsc.get(0);
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

    private static long n(Long value) {
        return value != null ? value : 0L;
    }
}
