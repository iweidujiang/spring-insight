package io.github.iweidujiang.springinsight.server.ai;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 上线前后变更归因：划分 before/after 窗口、对比服务指标 / 新增错误类型 / 新增依赖边。
 *
 * @author 苏渡苇
 * @since 2026-10-23
 */
public final class InsightAiChangeAttribution {

    /** 单侧窗口最大小时数。 */
    public static final int MAX_HOURS = 168;

    /** 默认窗口小时。 */
    public static final int DEFAULT_HOURS = 2;

    /** 喂给模型的最大变更条数。 */
    public static final int MAX_CHANGES = 20;

    /** 错误率绝对差阈值（百分点）。 */
    public static final double ERROR_RATE_DELTA_PP = 1.0;

    /** p95 绝对差阈值（毫秒）。 */
    public static final long P95_DELTA_MS = 50L;

    /** p95 相对差阈值。 */
    public static final double P95_RELATIVE = 0.20;

    /** 请求量相对差阈值。 */
    public static final double REQUEST_RELATIVE = 0.20;

    /** 请求量绝对差下限。 */
    public static final long REQUEST_DELTA_MIN = 5L;

    private InsightAiChangeAttribution() {
    }

    /**
     * 规范化小时参数（1..{@link #MAX_HOURS}，缺省 {@link #DEFAULT_HOURS}）。
     *
     * @param hours 原始值
     * @return 合法小时数
     */
    public static int clampHours(int hours) {
        if (hours <= 0) {
            return DEFAULT_HOURS;
        }
        return Math.min(hours, MAX_HOURS);
    }

    /**
     * 计算 before / after 半开窗口。
     * <ul>
     *   <li>after：{@code [now - hoursAfter, now)}</li>
     *   <li>before：{@code [now - hoursBefore - hoursAfter, now - hoursAfter)}</li>
     * </ul>
     *
     * @param hoursBefore 上线前时长（小时）
     * @param hoursAfter  上线后时长（小时）
     * @param nowMs       当前 epoch ms
     * @return 窗口边界
     */
    public static WindowBounds bounds(int hoursBefore, int hoursAfter, long nowMs) {
        int hb = clampHours(hoursBefore);
        int ha = clampHours(hoursAfter);
        long afterMs = ha * 3_600_000L;
        long beforeMs = hb * 3_600_000L;
        long afterFrom = nowMs - afterMs;
        long beforeFrom = afterFrom - beforeMs;
        return new WindowBounds(beforeFrom, afterFrom, afterFrom, nowMs, hb, ha);
    }

    /**
     * 对比两窗聚合结果，产出指标差异与新增错误类型 / 依赖边。
     *
     * @param beforeWindow {@link io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService#summarizeChangeWindow}
     * @param afterWindow  同上（上线后）
     * @return 变更行（最多 {@link #MAX_CHANGES}）
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> diffChanges(Map<String, Object> beforeWindow,
                                                        Map<String, Object> afterWindow) {
        Map<String, Object> before = beforeWindow != null ? beforeWindow : Map.of();
        Map<String, Object> after = afterWindow != null ? afterWindow : Map.of();

        Map<String, Map<String, Object>> beforeSvc = indexByService(
                (List<Map<String, Object>>) before.getOrDefault("services", List.of()));
        Map<String, Map<String, Object>> afterSvc = indexByService(
                (List<Map<String, Object>>) after.getOrDefault("services", List.of()));

        List<Map<String, Object>> changes = new ArrayList<>();
        Set<String> names = new HashSet<>();
        names.addAll(beforeSvc.keySet());
        names.addAll(afterSvc.keySet());
        for (String name : names) {
            Map<String, Object> b = beforeSvc.getOrDefault(name, emptyService());
            Map<String, Object> a = afterSvc.getOrDefault(name, emptyService());
            appendMetricIfSignificant(changes, "error_rate", name,
                    num(b.get("errorRate")), num(a.get("errorRate")), true);
            appendMetricIfSignificant(changes, "p95_ms", name,
                    num(b.get("p95Ms")), num(a.get("p95Ms")), false);
            appendMetricIfSignificant(changes, "request_count", name,
                    num(b.get("requestCount")), num(a.get("requestCount")), false);
        }

        Set<String> beforeErrors = errorTypeKeys(
                (List<Map<String, Object>>) before.getOrDefault("errorTypes", List.of()));
        List<Map<String, Object>> afterErrors =
                (List<Map<String, Object>>) after.getOrDefault("errorTypes", List.of());
        for (Map<String, Object> row : afterErrors) {
            String key = errorTypeKey(row);
            if (!beforeErrors.contains(key)) {
                Map<String, Object> ch = new LinkedHashMap<>();
                ch.put("metric", "new_error_type");
                ch.put("service", "");
                ch.put("before", 0);
                ch.put("after", num(row.get("count")));
                ch.put("delta", num(row.get("count")));
                ch.put("label", str(row.get("label")));
                ch.put("key", str(row.get("key")));
                ch.put("category", str(row.get("category")));
                changes.add(ch);
            }
        }

        Set<String> beforeEdges = edgeKeys(
                (List<Map<String, Object>>) before.getOrDefault("edges", List.of()));
        List<Map<String, Object>> afterEdges =
                (List<Map<String, Object>>) after.getOrDefault("edges", List.of());
        for (Map<String, Object> row : afterEdges) {
            String ek = edgeKey(row);
            if (!beforeEdges.contains(ek)) {
                Map<String, Object> ch = new LinkedHashMap<>();
                ch.put("metric", "new_dependency");
                ch.put("service", str(row.get("sourceService")));
                ch.put("before", 0);
                ch.put("after", num(row.get("callCount")));
                ch.put("delta", num(row.get("callCount")));
                ch.put("target", str(row.get("targetService")));
                changes.add(ch);
            }
        }

        changes.sort(Comparator
                .comparingInt((Map<String, Object> c) -> metricPriority(str(c.get("metric"))))
                .thenComparing((Map<String, Object> c) -> Math.abs(num(c.get("delta"))),
                        Comparator.reverseOrder()));
        if (changes.size() > MAX_CHANGES) {
            return new ArrayList<>(changes.subList(0, MAX_CHANGES));
        }
        return changes;
    }

    /**
     * 基于事实变更生成一句话摘要（无 AI 时也可展示）。
     *
     * @param changes     变更行
     * @param hoursBefore 上线前小时
     * @param hoursAfter  上线后小时
     * @return 摘要
     */
    public static String factSummary(List<Map<String, Object>> changes, int hoursBefore, int hoursAfter) {
        if (changes == null || changes.isEmpty()) {
            return "近 " + hoursAfter + " 小时相对上线前 " + hoursBefore
                    + " 小时，未发现显著的错误率 / 延迟 / 请求量变化或新增错误类型与依赖。";
        }
        for (Map<String, Object> c : changes) {
            String metric = str(c.get("metric"));
            if ("error_rate".equals(metric) && num(c.get("delta")) > 0) {
                return "上线后 " + str(c.get("service")) + " 错误率从 "
                        + formatNum(num(c.get("before"))) + "% 升至 "
                        + formatNum(num(c.get("after"))) + "%";
            }
        }
        for (Map<String, Object> c : changes) {
            if ("new_error_type".equals(str(c.get("metric")))) {
                String label = str(c.get("label"));
                return "上线后出现新的错误类型：" + (StringUtils.hasText(label) ? label : str(c.get("key")));
            }
        }
        for (Map<String, Object> c : changes) {
            if ("p95_ms".equals(str(c.get("metric"))) && num(c.get("delta")) > 0) {
                return "上线后 " + str(c.get("service")) + " p95 从 "
                        + ((long) num(c.get("before"))) + "ms 升至 "
                        + ((long) num(c.get("after"))) + "ms";
            }
        }
        for (Map<String, Object> c : changes) {
            if ("new_dependency".equals(str(c.get("metric")))) {
                return "上线后新增依赖边 " + str(c.get("service")) + " → " + str(c.get("target"));
            }
        }
        Map<String, Object> first = changes.get(0);
        return "上线后 " + str(first.get("service")) + " 的 " + str(first.get("metric"))
                + " 变化 Δ=" + formatNum(num(first.get("delta")));
    }

    /**
     * 构造喂给模型的精简事实。
     *
     * @param bounds  窗口
     * @param before  before 窗聚合
     * @param after   after 窗聚合
     * @param changes 差异行
     * @return payload
     */
    public static Map<String, Object> buildPayload(WindowBounds bounds,
                                                   Map<String, Object> before,
                                                   Map<String, Object> after,
                                                   List<Map<String, Object>> changes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hoursBefore", bounds.hoursBefore());
        payload.put("hoursAfter", bounds.hoursAfter());
        payload.put("beforeFromEpochMs", bounds.beforeFrom());
        payload.put("beforeToEpochMs", bounds.beforeTo());
        payload.put("afterFromEpochMs", bounds.afterFrom());
        payload.put("afterToEpochMs", bounds.afterTo());
        payload.put("beforeSpanCount", before != null ? before.getOrDefault("spanCount", 0) : 0);
        payload.put("afterSpanCount", after != null ? after.getOrDefault("spanCount", 0) : 0);
        payload.put("changes", changes != null ? changes : List.of());
        return payload;
    }

    private static void appendMetricIfSignificant(List<Map<String, Object>> out,
                                                  String metric,
                                                  String service,
                                                  double before,
                                                  double after,
                                                  boolean errorRate) {
        double delta = after - before;
        if (!isSignificant(metric, before, after, delta, errorRate)) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("metric", metric);
        row.put("service", service);
        row.put("before", round2(before));
        row.put("after", round2(after));
        row.put("delta", round2(delta));
        out.add(row);
    }

    private static boolean isSignificant(String metric, double before, double after,
                                         double delta, boolean errorRate) {
        if (errorRate) {
            if (Math.abs(delta) >= ERROR_RATE_DELTA_PP) {
                return true;
            }
            return before <= 0 && after > 0;
        }
        if ("p95_ms".equals(metric)) {
            if (Math.abs(delta) < P95_DELTA_MS) {
                return false;
            }
            double base = Math.max(before, 1.0);
            return Math.abs(delta) / base >= P95_RELATIVE || before <= 0 && after >= P95_DELTA_MS;
        }
        if ("request_count".equals(metric)) {
            if (Math.abs(delta) < REQUEST_DELTA_MIN) {
                return false;
            }
            double base = Math.max(before, 1.0);
            return Math.abs(delta) / base >= REQUEST_RELATIVE || before <= 0 && after >= REQUEST_DELTA_MIN;
        }
        return Math.abs(delta) > 0;
    }

    private static Map<String, Map<String, Object>> indexByService(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            String name = str(row.get("serviceName"));
            if (StringUtils.hasText(name)) {
                map.put(name, row);
            }
        }
        return map;
    }

    private static Map<String, Object> emptyService() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestCount", 0L);
        m.put("errorCount", 0L);
        m.put("errorRate", 0.0);
        m.put("p95Ms", 0L);
        return m;
    }

    private static Set<String> errorTypeKeys(List<Map<String, Object>> rows) {
        Set<String> keys = new HashSet<>();
        if (rows == null) {
            return keys;
        }
        for (Map<String, Object> row : rows) {
            keys.add(errorTypeKey(row));
        }
        return keys;
    }

    private static String errorTypeKey(Map<String, Object> row) {
        return str(row.get("category")) + "|" + str(row.get("key"));
    }

    private static Set<String> edgeKeys(List<Map<String, Object>> rows) {
        Set<String> keys = new HashSet<>();
        if (rows == null) {
            return keys;
        }
        for (Map<String, Object> row : rows) {
            keys.add(edgeKey(row));
        }
        return keys;
    }

    private static String edgeKey(Map<String, Object> row) {
        return str(row.get("sourceService")) + "->" + str(row.get("targetService"));
    }

    private static int metricPriority(String metric) {
        return switch (metric) {
            case "error_rate" -> 0;
            case "new_error_type" -> 1;
            case "p95_ms" -> 2;
            case "new_dependency" -> 3;
            case "request_count" -> 4;
            default -> 9;
        };
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

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String formatNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.valueOf(round2(v));
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * before / after 半开窗口边界。
     *
     * @param beforeFrom  before 起始（含）
     * @param beforeTo    before 结束（不含）= after 起始
     * @param afterFrom   after 起始（含）
     * @param afterTo     after 结束（不含）
     * @param hoursBefore 上线前小时
     * @param hoursAfter  上线后小时
     */
    public record WindowBounds(long beforeFrom,
                               long beforeTo,
                               long afterFrom,
                               long afterTo,
                               int hoursBefore,
                               int hoursAfter) {
    }
}
