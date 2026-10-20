package io.github.iweidujiang.springinsight.server.health;

import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务健康评分：错误率、p95 延迟与是否有数据加权合成 0~100 分。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
@Service
@RequiredArgsConstructor
public class HealthScoreService {

    private static final int MAX_SERVICES = 10_000;

    private final TraceSpanPersistenceService persistenceService;

    /**
     * 按时间窗聚合各服务健康分（按分数降序）。
     *
     * @param hours 时间窗小时；{@code <=0} 表示不设下界
     * @return 导出行列表
     */
    public List<Map<String, Object>> listServiceHealth(int hours) {
        List<Map<String, Object>> latency =
                persistenceService.getServiceLatencySummaries(hours, MAX_SERVICES);
        List<Map<String, Object>> out = new ArrayList<>(latency.size());
        for (Map<String, Object> row : latency) {
            if (row == null) {
                continue;
            }
            String service = str(row.get("service_name"));
            if (service.isBlank()) {
                continue;
            }
            double errorRate = num(row.get("error_rate"));
            long p95 = Math.round(num(row.get("p95_ms")));
            long spanCount = Math.round(num(row.get("span_count")));
            out.add(toMap(score(service, errorRate, p95, spanCount > 0)));
        }
        out.sort(Comparator
                .comparingInt((Map<String, Object> m) -> ((Number) m.get("score")).intValue())
                .reversed()
                .thenComparing(m -> String.valueOf(m.get("service"))));
        return out;
    }

    /**
     * 计算单个服务健康分。
     *
     * @param service   服务名
     * @param errorRate 错误率百分比（0~100）
     * @param p95Ms     p95 耗时毫秒
     * @param hasData   时间窗内是否有 Span
     * @return 评分结果
     */
    public HealthScore score(String service, double errorRate, long p95Ms, boolean hasData) {
        double errorPart = hasData ? errorScore(errorRate) : 0.0;
        double latencyPart = hasData ? latencyScore(p95Ms) : 0.0;
        double availabilityPart = hasData ? 100.0 : 20.0;
        int total = (int) Math.round(errorPart * 0.5 + latencyPart * 0.3 + availabilityPart * 0.2);
        if (total < 0) {
            total = 0;
        } else if (total > 100) {
            total = 100;
        }
        String level = hasData ? levelOf(total) : "unknown";
        return new HealthScore(
                service == null ? "" : service,
                total,
                level,
                round1(errorRate),
                Math.max(0L, p95Ms),
                hasData);
    }

    /**
     * 错误率得分：0%→100，1%→80，5%→40，10%+→0（分段线性插值）。
     *
     * @param errorRatePct 错误率百分比
     * @return 0~100
     */
    public static double errorScore(double errorRatePct) {
        double r = Math.max(0.0, errorRatePct);
        if (r <= 0.0) {
            return 100.0;
        }
        if (r <= 1.0) {
            return lerp(r, 0.0, 1.0, 100.0, 80.0);
        }
        if (r <= 5.0) {
            return lerp(r, 1.0, 5.0, 80.0, 40.0);
        }
        if (r <= 10.0) {
            return lerp(r, 5.0, 10.0, 40.0, 0.0);
        }
        return 0.0;
    }

    /**
     * p95 延迟得分：&lt;100ms→100，&lt;500→70，&lt;1s→40，≥1s→0。
     *
     * @param p95Ms p95 毫秒
     * @return 0~100
     */
    public static double latencyScore(long p95Ms) {
        if (p95Ms < 100L) {
            return 100.0;
        }
        if (p95Ms < 500L) {
            return 70.0;
        }
        if (p95Ms < 1000L) {
            return 40.0;
        }
        return 0.0;
    }

    /**
     * 按总分分级（仅用于有数据的服务）。
     *
     * @param score 0~100
     * @return healthy / warning / degraded / critical
     */
    public static String levelOf(int score) {
        if (score >= 80) {
            return "healthy";
        }
        if (score >= 60) {
            return "warning";
        }
        if (score >= 40) {
            return "degraded";
        }
        return "critical";
    }

    /**
     * 单服务健康分结果。
     *
     * @param service   服务名
     * @param score     0~100
     * @param level     分级；无数据时为 {@code unknown}
     * @param errorRate 错误率百分比
     * @param p95       p95 毫秒
     * @param hasData   是否有数据
     */
    public record HealthScore(
            String service,
            int score,
            String level,
            double errorRate,
            long p95,
            boolean hasData) {
    }

    private static Map<String, Object> toMap(HealthScore hs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service", hs.service());
        m.put("score", hs.score());
        m.put("level", hs.level());
        m.put("errorRate", hs.errorRate());
        m.put("p95", hs.p95());
        m.put("hasData", hs.hasData());
        return m;
    }

    private static double lerp(double x, double x0, double x1, double y0, double y1) {
        if (x1 == x0) {
            return y0;
        }
        return y0 + (x - x0) / (x1 - x0) * (y1 - y0);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static double num(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }
}
