package io.github.iweidujiang.springinsight.server.slo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 错误预算纯计算（无 IO）。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
public final class ErrorBudgetCalculator {

    private ErrorBudgetCalculator() {
    }

    /**
     * 根据目标可用性与窗口内错误/总请求计算预算。
     *
     * @param serviceName       服务名
     * @param targetAvailability 目标（0~1）
     * @param windowHours       SLO 配置窗口
     * @param elapsedHours      实际统计窗口（通常 ≤ windowHours）
     * @param totalCount        窗口内 Span / 请求数
     * @param errorCount        窗口内错误数
     * @param nowMs             当前时刻（用于预计耗尽时间）
     * @return 预算结果 Map
     */
    public static Map<String, Object> compute(String serviceName,
                                              double targetAvailability,
                                              long windowHours,
                                              long elapsedHours,
                                              long totalCount,
                                              long errorCount,
                                              long nowMs) {
        double target = clampTarget(targetAvailability);
        long window = windowHours > 0 ? windowHours : ServiceSlo.DEFAULT_WINDOW_HOURS;
        long elapsed = Math.max(1L, elapsedHours > 0 ? elapsedHours : window);

        double budgetTotal = 1.0 - target;
        double actualAvailability;
        if (totalCount <= 0) {
            actualAvailability = 1.0;
        } else {
            double err = Math.max(0L, errorCount);
            double total = totalCount;
            actualAvailability = 1.0 - (err / total);
            if (actualAvailability < 0) {
                actualAvailability = 0;
            }
            if (actualAvailability > 1) {
                actualAvailability = 1;
            }
        }
        double budgetConsumed = 1.0 - actualAvailability;
        double budgetRemaining = budgetTotal - budgetConsumed;

        double denom = (elapsed / (double) window) * budgetTotal;
        double burnRate = denom > 1e-15 ? budgetConsumed / denom : 0.0;

        String status = statusOf(budgetRemaining, budgetTotal);
        String estimatedExhaustion = estimatedExhaustionIso(
                budgetRemaining, budgetConsumed, elapsed, nowMs);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", serviceName != null ? serviceName : "");
        body.put("target", round6(target));
        body.put("actualAvailability", round6(actualAvailability));
        body.put("budgetTotal", round6(budgetTotal));
        body.put("budgetConsumed", round6(budgetConsumed));
        body.put("budgetRemaining", round6(budgetRemaining));
        body.put("status", status);
        body.put("burnRate", round4(burnRate));
        body.put("estimatedExhaustion", estimatedExhaustion);
        body.put("windowHours", window);
        body.put("elapsedHours", elapsed);
        body.put("totalCount", totalCount);
        body.put("errorCount", Math.max(0L, errorCount));
        return body;
    }

    /**
     * @param budgetRemaining 剩余预算（比例）
     * @param budgetTotal     总预算
     * @return healthy / warning / exhausted
     */
    static String statusOf(double budgetRemaining, double budgetTotal) {
        if (budgetRemaining < 0) {
            return "exhausted";
        }
        if (budgetTotal <= 1e-15) {
            return budgetRemaining < 0 ? "exhausted" : "healthy";
        }
        double ratio = budgetRemaining / budgetTotal;
        if (ratio > 0.5) {
            return "healthy";
        }
        return "warning";
    }

    /**
     * @param budgetRemaining 剩余
     * @param budgetConsumed  已消耗
     * @param elapsedHours    已过小时
     * @param nowMs           当前 epoch ms
     * @return ISO-8601 UTC；无法估计时为空串
     */
    static String estimatedExhaustionIso(double budgetRemaining,
                                         double budgetConsumed,
                                         long elapsedHours,
                                         long nowMs) {
        if (budgetRemaining < 0) {
            return Instant.ofEpochMilli(nowMs).truncatedTo(ChronoUnit.SECONDS).toString();
        }
        if (budgetConsumed <= 1e-15 || elapsedHours <= 0) {
            return "";
        }
        double hoursLeft = budgetRemaining / (budgetConsumed / (double) elapsedHours);
        if (hoursLeft > 365 * 24 * 10) {
            return "";
        }
        long exhaustAt = nowMs + (long) (hoursLeft * 3_600_000L);
        return Instant.ofEpochMilli(exhaustAt).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static double clampTarget(double target) {
        if (Double.isNaN(target) || target <= 0) {
            return 0.999;
        }
        if (target > 1.0) {
            return 1.0;
        }
        return target;
    }

    private static double round6(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
