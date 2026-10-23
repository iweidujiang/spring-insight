package io.github.iweidujiang.springinsight.server.anomaly;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无阈值异常检测：按 {@code (metric, service)} 维护滑动窗口，支持 Z-score 与环比突变。
 * <p>
 * 在告警扫描采集到当前指标后调用 {@link #detect}；结果可挂到告警体，也可供 UI 标记。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026-10-23
 */
@Service
public class AnomalyDetector {

    /** 每个键最多保留的历史点数。 */
    public static final int MAX_WINDOW = 100;

    /** Z-score / 环比判定所需最少样本数。 */
    public static final int MIN_SAMPLES = 10;

    /** |Z| 超过该值视为异常。 */
    public static final double Z_THRESHOLD = 3.0;

    /** 相对上一同等时段变化超过该比例视为异常。 */
    public static final double PERIOD_CHANGE_RATIO = 0.50;

    private final ConcurrentHashMap<String, Deque<Double>> windows = new ConcurrentHashMap<>();

    /** 最近一次检测结果：key → 结果（含非异常，便于 UI 读当前态）。 */
    private final ConcurrentHashMap<String, AnomalyDetectionResult> latest = new ConcurrentHashMap<>();

    /** 最近触发的异常（环形，供列表 API）。 */
    private final Deque<Map<String, Object>> recentAnomalies = new ArrayDeque<>();

    private static final int MAX_RECENT = 50;

    /**
     * 写入当前值并检测是否异常；样本不足时返回 {@code insufficientData}。
     *
     * @param metric       指标名（error_rate / error_count / latency_p95 / span_count）
     * @param serviceName  服务名
     * @param currentValue 当前窗口聚合值
     * @return 检测结果（始终非 null）
     */
    public AnomalyDetectionResult detect(String metric, String serviceName, double currentValue) {
        String m = normalizeMetric(metric);
        String svc = serviceName == null ? "" : serviceName.trim();
        String key = key(m, svc);

        Deque<Double> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>(MAX_WINDOW));
        List<Double> history;
        synchronized (window) {
            history = new ArrayList<>(window);
        }

        AnomalyDetectionResult result;
        if (history.size() < MIN_SAMPLES) {
            result = AnomalyDetectionResult.insufficient(currentValue, history.size());
        } else {
            result = evaluate(m, currentValue, history);
        }

        synchronized (window) {
            window.addLast(currentValue);
            while (window.size() > MAX_WINDOW) {
                window.removeFirst();
            }
        }

        latest.put(key, result);
        if (result.isAnomaly()) {
            recordRecent(m, svc, result);
        }
        return result;
    }

    /**
     * @param metric      指标
     * @param serviceName 服务
     * @return 该键最近一次检测；无则 null
     */
    public AnomalyDetectionResult latest(String metric, String serviceName) {
        return latest.get(key(normalizeMetric(metric), serviceName == null ? "" : serviceName.trim()));
    }

    /**
     * 某服务当前是否存在任一指标异常。
     *
     * @param serviceName 服务名
     * @return 若有异常返回其中一条；否则 null
     */
    public AnomalyDetectionResult latestAnomalyForService(String serviceName) {
        String svc = serviceName == null ? "" : serviceName.trim();
        AnomalyDetectionResult hit = null;
        for (Map.Entry<String, AnomalyDetectionResult> e : latest.entrySet()) {
            if (!e.getKey().endsWith("\0" + svc)) {
                continue;
            }
            AnomalyDetectionResult r = e.getValue();
            if (r != null && r.isAnomaly()) {
                hit = r;
                break;
            }
        }
        return hit;
    }

    /**
     * @param limit 条数上限
     * @return 最近异常列表（新在前）
     */
    public List<Map<String, Object>> recentAnomalies(int limit) {
        int max = Math.max(1, Math.min(limit, MAX_RECENT));
        synchronized (recentAnomalies) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : recentAnomalies) {
                out.add(row);
                if (out.size() >= max) {
                    break;
                }
            }
            return out;
        }
    }

    /**
     * 清空窗口与最近结果（单测用）。
     */
    public void reset() {
        windows.clear();
        latest.clear();
        synchronized (recentAnomalies) {
            recentAnomalies.clear();
        }
    }

    /**
     * @param key 窗口键
     * @return 当前窗口长度（单测）
     */
    int windowSize(String metric, String serviceName) {
        Deque<Double> w = windows.get(key(normalizeMetric(metric), serviceName == null ? "" : serviceName.trim()));
        return w == null ? 0 : w.size();
    }

    private AnomalyDetectionResult evaluate(String metric, double current, List<Double> history) {
        Stats stats = stats(history);
        if (stats.std > 1e-12) {
            double z = (current - stats.mean) / stats.std;
            if (Math.abs(z) > Z_THRESHOLD) {
                double zRounded = Math.round(z * 10000.0) / 10000.0;
                return new AnomalyDetectionResult(
                        true,
                        "z-score",
                        zRounded,
                        round4(stats.mean),
                        round4(stats.std),
                        current,
                        zScoreMessage(metric, current, stats.mean, zRounded),
                        history.size());
            }
        }

        double prevPeriod = previousPeriodMean(history);
        double base = Math.max(Math.abs(prevPeriod), 1e-9);
        double rel = Math.abs(current - prevPeriod) / base;
        if (rel > PERIOD_CHANGE_RATIO) {
            return new AnomalyDetectionResult(
                    true,
                    "period-over-period",
                    null,
                    round4(stats.mean),
                    round4(stats.std),
                    current,
                    periodMessage(metric, current, prevPeriod, rel),
                    history.size());
        }

        return AnomalyDetectionResult.normal(current, stats.mean, stats.std, history.size());
    }

    private void recordRecent(String metric, String service, AnomalyDetectionResult result) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("serviceName", service);
        row.put("metric", metric);
        row.put("detectedAt", System.currentTimeMillis());
        row.putAll(result.toMap());
        synchronized (recentAnomalies) {
            recentAnomalies.addFirst(row);
            while (recentAnomalies.size() > MAX_RECENT) {
                recentAnomalies.removeLast();
            }
        }
    }

    private static String zScoreMessage(String metric, double current, double mean, double z) {
        String label = metricLabel(metric);
        String unit = metricUnit(metric);
        String dir = z >= 0 ? "高于" : "低于";
        return label + " " + format(current) + unit + " 显著" + dir + "历史均值 "
                + format(mean) + unit + "（Z=" + format(z) + "）";
    }

    private static String periodMessage(String metric, double current, double previous, double rel) {
        String label = metricLabel(metric);
        String unit = metricUnit(metric);
        int pct = (int) Math.round(rel * 100.0);
        return label + " " + format(current) + unit + " 相对上一同等时段 "
                + format(previous) + unit + " 变化约 " + pct + "%";
    }

    private static String metricLabel(String metric) {
        return switch (metric) {
            case "error_rate" -> "错误率";
            case "error_count" -> "错误次数";
            case "latency_p95" -> "p95 延迟";
            case "span_count" -> "请求量";
            default -> metric;
        };
    }

    private static String metricUnit(String metric) {
        return switch (metric) {
            case "error_rate" -> "%";
            case "latency_p95" -> "ms";
            default -> "";
        };
    }

    private static String format(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    private static double previousPeriodMean(List<Double> history) {
        int n = history.size();
        int half = Math.max(1, n / 2);
        double sum = 0.0;
        for (int i = 0; i < half; i++) {
            sum += history.get(i);
        }
        return sum / half;
    }

    private static Stats stats(List<Double> values) {
        int n = values.size();
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        double mean = sum / n;
        double var = 0.0;
        for (double v : values) {
            double d = v - mean;
            var += d * d;
        }
        // 样本标准差
        double std = n > 1 ? Math.sqrt(var / (n - 1)) : 0.0;
        return new Stats(mean, std);
    }

    private static String key(String metric, String service) {
        return metric + "\0" + service;
    }

    private static String normalizeMetric(String metric) {
        if (!StringUtils.hasText(metric)) {
            return "";
        }
        return metric.trim().toLowerCase(Locale.ROOT);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private record Stats(double mean, double std) {
    }
}
