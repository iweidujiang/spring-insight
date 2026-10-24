package io.github.iweidujiang.springinsight.server.topology;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 两时段拓扑边对比：新增 / 消失 / 调用量显著变化。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
public final class TopologyDiff {

    /** 相对调用量变化阈值（例如 0.50 = 50%）。 */
    public static final double CHANGE_RATIO = 0.50;

    private TopologyDiff() {
    }

    /**
     * 对比两时段依赖边。
     * <p>
     * 入参行兼容 {@code source_service}/{@code target_service}/{@code call_count}/{@code last_seen}，
     * 也兼容 {@code from}/{@code to}/{@code count}。
     * </p>
     *
     * @param edgesA 基线时段（hoursA）
     * @param edgesB 对比时段（hoursB）
     * @return added / removed / changed
     */
    public static Map<String, Object> diff(List<Map<String, Object>> edgesA,
                                           List<Map<String, Object>> edgesB) {
        return diff(edgesA, edgesB, CHANGE_RATIO);
    }

    /**
     * @param edgesA     基线
     * @param edgesB     对比
     * @param changeRatio 相对变化阈值；{@code <=0} 表示不过滤 changed
     * @return 差异体
     */
    public static Map<String, Object> diff(List<Map<String, Object>> edgesA,
                                           List<Map<String, Object>> edgesB,
                                           double changeRatio) {
        Map<String, Edge> before = index(edgesA);
        Map<String, Edge> after = index(edgesB);

        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();

        for (Map.Entry<String, Edge> e : after.entrySet()) {
            Edge b = e.getValue();
            Edge a = before.get(e.getKey());
            if (a == null) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("from", b.from);
                row.put("to", b.to);
                row.put("count", b.count);
                added.add(row);
                continue;
            }
            if (isSignificantChange(a.count, b.count, changeRatio)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("from", b.from);
                row.put("to", b.to);
                row.put("countBefore", a.count);
                row.put("countAfter", b.count);
                row.put("changeRate", changeRate(a.count, b.count));
                changed.add(row);
            }
        }
        for (Map.Entry<String, Edge> e : before.entrySet()) {
            if (after.containsKey(e.getKey())) {
                continue;
            }
            Edge a = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", a.from);
            row.put("to", a.to);
            if (StringUtils.hasText(a.lastSeen)) {
                row.put("lastSeen", a.lastSeen);
            } else {
                row.put("lastSeen", "");
            }
            removed.add(row);
        }

        added.sort(Comparator.comparing((Map<String, Object> r) -> str(r.get("from")))
                .thenComparing(r -> str(r.get("to"))));
        removed.sort(Comparator.comparing((Map<String, Object> r) -> str(r.get("from")))
                .thenComparing(r -> str(r.get("to"))));
        changed.sort(Comparator.comparing((Map<String, Object> r) ->
                        Math.abs(num(r.get("changeRate")))).reversed());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("added", added);
        body.put("removed", removed);
        body.put("changed", changed);
        return body;
    }

    /**
     * @param before 基线调用量
     * @param after  对比调用量
     * @param ratio  相对阈值
     * @return 是否显著
     */
    static boolean isSignificantChange(long before, long after, double ratio) {
        if (ratio <= 0) {
            return before != after;
        }
        if (before <= 0) {
            return after > 0;
        }
        double rel = Math.abs(after - before) / (double) before;
        return rel > ratio;
    }

    /**
     * @param before 基线
     * @param after  对比
     * @return {@code (after - before) / before}；before 为 0 时若 after&gt;0 返回 after
     */
    static double changeRate(long before, long after) {
        if (before <= 0) {
            return after > 0 ? after : 0.0;
        }
        return Math.round(((after - before) / (double) before) * 10000.0) / 10000.0;
    }

    private static Map<String, Edge> index(List<Map<String, Object>> edges) {
        Map<String, Edge> map = new LinkedHashMap<>();
        if (edges == null) {
            return map;
        }
        for (Map<String, Object> raw : edges) {
            if (raw == null) {
                continue;
            }
            String from = firstNonBlank(raw, "from", "source_service", "sourceService");
            String to = firstNonBlank(raw, "to", "target_service", "targetService");
            if (!StringUtils.hasText(from) || !StringUtils.hasText(to)) {
                continue;
            }
            long count = (long) num(firstPresent(raw, "count", "call_count", "callCount"));
            String lastSeen = firstNonBlank(raw, "lastSeen", "last_seen");
            map.put(from + "\0" + to, new Edge(from, to, Math.max(0L, count), lastSeen));
        }
        return map;
    }

    private static String firstNonBlank(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object v = raw.get(key);
            if (v != null && StringUtils.hasText(String.valueOf(v)) && !"null".equals(String.valueOf(v))) {
                return String.valueOf(v).trim();
            }
        }
        return "";
    }

    private static Object firstPresent(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            if (raw.containsKey(key) && raw.get(key) != null) {
                return raw.get(key);
            }
        }
        return 0;
    }

    private static double num(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private record Edge(String from, String to, long count, String lastSeen) {
    }
}
