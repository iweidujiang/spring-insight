package io.github.iweidujiang.springinsight.server.ai;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace 对比：按 {@code operationName + remoteService} 对齐 Span，计算耗时差并筛出显著差异。
 *
 * @author 苏渡苇
 * @since 2026-10-22
 */
public final class InsightAiTraceCompare {

    /** 相对耗时差阈值（例如 0.20 = 20%）。 */
    public static final double SIGNIFICANT_RATIO = 0.20;

    /** 喂给模型的最大差异条数。 */
    public static final int MAX_DIFF_ROWS = 20;

    private InsightAiTraceCompare() {
    }

    /**
     * 对齐两条 Trace 中同名操作，筛出相对差异超过阈值的行（按 |Δ| 降序）。
     *
     * @param spansA Trace A 的 Span 列表
     * @param spansB Trace B 的 Span 列表
     * @return 显著差异行
     */
    public static List<DiffRow> significantDiffs(List<TraceSpan> spansA, List<TraceSpan> spansB) {
        return significantDiffs(spansA, spansB, SIGNIFICANT_RATIO, MAX_DIFF_ROWS);
    }

    /**
     * @param spansA   Trace A
     * @param spansB   Trace B
     * @param ratio    相对差阈值；{@code <=0} 表示不过滤
     * @param maxRows  最多返回条数
     * @return 差异行
     */
    public static List<DiffRow> significantDiffs(List<TraceSpan> spansA,
                                                List<TraceSpan> spansB,
                                                double ratio,
                                                int maxRows) {
        Map<String, Agg> left = aggregate(spansA);
        Map<String, Agg> right = aggregate(spansB);
        List<DiffRow> rows = new ArrayList<>();
        for (Map.Entry<String, Agg> e : left.entrySet()) {
            String key = e.getKey();
            Agg a = e.getValue();
            Agg b = right.get(key);
            if (b == null) {
                continue;
            }
            long durationA = a.maxMs;
            long durationB = b.maxMs;
            long delta = durationB - durationA;
            double rel = relativeDiff(durationA, durationB);
            if (ratio > 0 && rel < ratio) {
                continue;
            }
            rows.add(new DiffRow(a.operationName, a.serviceLabel, durationA, durationB, delta, rel));
        }
        rows.sort(Comparator.comparingLong((DiffRow r) -> Math.abs(r.delta())).reversed());
        int limit = Math.max(1, Math.min(maxRows, 100));
        return rows.size() <= limit ? rows : rows.subList(0, limit);
    }

    /**
     * Trace 根耗时（无父或最早根的 duration；取最长根作为总耗时近似）。
     *
     * @param spans Span 列表
     * @return 总耗时毫秒
     */
    public static long rootDurationMs(List<TraceSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return 0L;
        }
        long max = 0L;
        for (TraceSpan s : spans) {
            if (s == null) {
                continue;
            }
            String parent = s.getParentSpanId();
            if (!StringUtils.hasText(parent)) {
                max = Math.max(max, durationOf(s));
            }
        }
        if (max > 0L) {
            return max;
        }
        for (TraceSpan s : spans) {
            if (s != null) {
                max = Math.max(max, durationOf(s));
            }
        }
        return max;
    }

    /**
     * 将差异行转为可序列化 Map（供 prompt / 响应）。
     *
     * @param rows 差异行
     * @return Map 列表
     */
    public static List<Map<String, Object>> toPayloadRows(List<DiffRow> rows) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (DiffRow row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("span", row.span());
            m.put("service", row.service());
            m.put("durationA", row.durationA());
            m.put("durationB", row.durationB());
            m.put("delta", row.delta());
            m.put("ratio", Math.round(row.ratio() * 1000.0) / 1000.0);
            list.add(m);
        }
        return list;
    }

    private static Map<String, Agg> aggregate(List<TraceSpan> spans) {
        Map<String, Agg> map = new LinkedHashMap<>();
        if (spans == null) {
            return map;
        }
        for (TraceSpan s : spans) {
            if (s == null) {
                continue;
            }
            String op = StringUtils.hasText(s.getOperationName()) ? s.getOperationName().trim() : "(unknown)";
            String remote = s.getRemoteService() != null ? s.getRemoteService().trim() : "";
            String key = op + "\0" + remote;
            String serviceLabel = StringUtils.hasText(remote)
                    ? remote
                    : (StringUtils.hasText(s.getServiceName()) ? s.getServiceName().trim() : "unknown");
            long dur = durationOf(s);
            Agg cur = map.get(key);
            if (cur == null) {
                map.put(key, new Agg(op, serviceLabel, dur));
            } else if (dur > cur.maxMs) {
                cur.maxMs = dur;
                if (StringUtils.hasText(serviceLabel)) {
                    cur.serviceLabel = serviceLabel;
                }
            }
        }
        return map;
    }

    private static long durationOf(TraceSpan s) {
        Long d = s.getDurationMs();
        return d == null || d < 0L ? 0L : d;
    }

    /**
     * 相对差异：{@code |B-A| / max(A, 1)}。
     *
     * @param durationA A 耗时
     * @param durationB B 耗时
     * @return 相对比例
     */
    static double relativeDiff(long durationA, long durationB) {
        long base = Math.max(durationA, 1L);
        return Math.abs(durationB - durationA) / (double) base;
    }

    /**
     * 一对对齐后的操作耗时差。
     *
     * @param span       操作名
     * @param service    展示用服务（优先 remoteService）
     * @param durationA  A 耗时
     * @param durationB  B 耗时
     * @param delta      B − A
     * @param ratio      相对差
     */
    public record DiffRow(String span,
                          String service,
                          long durationA,
                          long durationB,
                          long delta,
                          double ratio) {
    }

    private static final class Agg {
        private final String operationName;
        private String serviceLabel;
        private long maxMs;

        private Agg(String operationName, String serviceLabel, long maxMs) {
            this.operationName = operationName;
            this.serviceLabel = serviceLabel;
            this.maxMs = maxMs;
        }
    }
}
