package io.github.iweidujiang.springinsight.storage.impl;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.storage.spi.TraceSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把 Span 收成链路摘要，供内存存储分页，并作为 SQLite 分组查询的对照。
 */
public final class TraceSummaryCalculator {

    private TraceSummaryCalculator() {
    }

    public static List<TraceSummary> collect(List<TraceSpan> spans, long sinceEpoch, String serviceName,
                                              String status, String query, long minDurationMs, String pathPrefix) {
        String svc = serviceName != null ? serviceName.trim() : "";
        String st = status != null ? status.trim().toLowerCase() : "all";
        String q = query != null ? query.trim().toLowerCase() : "";
        String pathPref = normalizePathPrefix(pathPrefix);
        long minDur = Math.max(0L, minDurationMs);
        long since = Math.max(0L, sinceEpoch);

        record Acc(
                String traceId,
                long minStart,
                long maxEnd,
                int spanCount,
                boolean hasError,
                LinkedHashSet<String> services,
                List<TraceSpan> spans
        ) {}

        Map<String, Acc> byTrace = new LinkedHashMap<>();
        if (spans != null) {
            for (TraceSpan s : spans) {
                if (s == null || s.getTraceId() == null || s.getTraceId().isBlank()) {
                    continue;
                }
                long start = n(s.getStartTime());
                if (start < since) {
                    continue;
                }
                long end = n(s.getEndTime());
                if (end <= 0) {
                    end = start + n(s.getDurationMs());
                }
                Acc acc = byTrace.get(s.getTraceId());
                if (acc == null) {
                    LinkedHashSet<String> services = new LinkedHashSet<>();
                    if (s.getServiceName() != null && !s.getServiceName().isBlank()) {
                        services.add(s.getServiceName());
                    }
                    List<TraceSpan> group = new ArrayList<>();
                    group.add(s);
                    byTrace.put(s.getTraceId(), new Acc(
                            s.getTraceId(), start, Math.max(start, end), 1, ServiceAggregateCalculator.isError(s), services, group));
                } else {
                    if (s.getServiceName() != null && !s.getServiceName().isBlank()) {
                        acc.services().add(s.getServiceName());
                    }
                    acc.spans().add(s);
                    byTrace.put(s.getTraceId(), new Acc(
                            acc.traceId(),
                            Math.min(acc.minStart(), start),
                            Math.max(acc.maxEnd(), Math.max(start, end)),
                            acc.spanCount() + 1,
                            acc.hasError() || ServiceAggregateCalculator.isError(s),
                            acc.services(),
                            acc.spans()));
                }
            }
        }

        List<TraceSummary> out = new ArrayList<>();
        for (Acc acc : byTrace.values()) {
            if (!svc.isEmpty() && !acc.services().contains(svc) && !spansTouchRemote(acc.spans(), svc)) {
                continue;
            }
            if ("error".equals(st) && !acc.hasError()) {
                continue;
            }
            if ("ok".equals(st) && acc.hasError()) {
                continue;
            }
            TraceSpan root = pickRoot(acc.spans());
            String rootOp = root != null && root.getOperationName() != null ? root.getOperationName() : "";
            String rootSvc = root != null && root.getServiceName() != null ? root.getServiceName() : "";
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
            if (!pathPref.isEmpty() && !operationMatchesPrefix(rootOp, pathPref)) {
                continue;
            }
            out.add(new TraceSummary(
                    acc.traceId(),
                    rootSvc,
                    rootOp.isBlank() ? "(unknown)" : rootOp,
                    acc.minStart(),
                    durationMs,
                    acc.spanCount(),
                    acc.services().size(),
                    acc.hasError(),
                    acc.hasError() ? "ERROR" : "OK"));
        }
        return out;
    }

    public static List<TraceSummary> page(List<TraceSummary> rows, int offset, int limit, String sort) {
        List<TraceSummary> sorted = new ArrayList<>(rows == null ? List.of() : rows);
        sorted.sort(comparator(sort));
        int off = Math.max(0, offset);
        int lim = Math.min(200, Math.max(1, limit));
        if (off >= sorted.size()) {
            return List.of();
        }
        return List.copyOf(sorted.subList(off, Math.min(sorted.size(), off + lim)));
    }

    public static Comparator<TraceSummary> comparator(String sort) {
        Comparator<TraceSummary> byId = Comparator.comparing(TraceSummary::traceId);
        return switch (normalizeSort(sort)) {
            case "time_asc" -> Comparator.comparingLong(TraceSummary::startTime).thenComparing(byId);
            case "duration_desc" -> Comparator.comparingLong(TraceSummary::durationMs).reversed().thenComparing(byId);
            case "duration_asc" -> Comparator.comparingLong(TraceSummary::durationMs).thenComparing(byId);
            default -> Comparator.comparingLong(TraceSummary::startTime).reversed().thenComparing(byId);
        };
    }

    public static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "time_desc";
        }
        return switch (sort.trim().toLowerCase()) {
            case "time_asc", "duration_desc", "duration_asc", "time_desc" -> sort.trim().toLowerCase();
            default -> "time_desc";
        };
    }

    public static Map<String, Object> toMap(TraceSummary row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("traceId", row.traceId());
        map.put("serviceName", row.serviceName());
        map.put("operationName", row.operationName());
        map.put("startTime", row.startTime());
        map.put("durationMs", row.durationMs());
        map.put("spanCount", row.spanCount());
        map.put("serviceCount", row.serviceCount());
        map.put("hasError", row.hasError());
        map.put("statusCode", row.statusCode());
        return map;
    }

    static TraceSpan pickRoot(List<TraceSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return null;
        }
        Set<String> ids = new HashSet<>();
        for (TraceSpan s : spans) {
            if (s.getSpanId() != null && !s.getSpanId().isBlank()) {
                ids.add(s.getSpanId());
            }
        }
        TraceSpan best = null;
        int bestRank = Integer.MAX_VALUE;
        int bestKindRank = Integer.MAX_VALUE;
        long bestStart = Long.MAX_VALUE;
        for (TraceSpan s : spans) {
            String parent = s.getParentSpanId();
            int rank;
            if (parent == null || parent.isBlank()) {
                rank = 0;
            } else if (!ids.contains(parent)) {
                rank = 1;
            } else {
                rank = 2;
            }
            int kindRank = "SERVER".equalsIgnoreCase(s.getSpanKind()) ? 0 : 1;
            long start = n(s.getStartTime());
            if (rank < bestRank
                    || (rank == bestRank && kindRank < bestKindRank)
                    || (rank == bestRank && kindRank == bestKindRank && start < bestStart)) {
                best = s;
                bestRank = rank;
                bestKindRank = kindRank;
                bestStart = start;
            }
        }
        return best;
    }

    private static boolean spansTouchRemote(List<TraceSpan> spans, String remote) {
        for (TraceSpan s : spans) {
            if (s != null && remote.equals(s.getRemoteService())) {
                return true;
            }
        }
        return false;
    }

    static String normalizePathPrefix(String pathPrefix) {
        if (pathPrefix == null) {
            return "";
        }
        String p = pathPrefix.trim();
        if (p.isEmpty()) {
            return "";
        }
        int sp = p.indexOf(' ');
        if (sp > 0 && p.substring(0, sp).matches("(?i)GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS")) {
            p = p.substring(sp + 1).trim();
        }
        if (p.isEmpty()) {
            return "";
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return p.toLowerCase();
    }

    static boolean operationMatchesPrefix(String operationName, String normalizedPrefix) {
        if (normalizedPrefix == null || normalizedPrefix.isEmpty()) {
            return true;
        }
        if (operationName == null || operationName.isBlank()) {
            return false;
        }
        String op = operationName.trim();
        int sp = op.indexOf(' ');
        String path = sp > 0 ? op.substring(sp + 1).trim() : op;
        if (path.isEmpty()) {
            return false;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path.toLowerCase().startsWith(normalizedPrefix);
    }

    private static long n(Long value) {
        return value != null ? value : 0L;
    }
}
