package io.github.iweidujiang.springinsight.server.alert;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条告警条件。多条条件同时成立才触发。
 *
 * @author 苏渡苇
 * @since 2026-10-19
 */
@Data
public class AlertCondition {

    /**
     * {@code error_rate}、{@code error_count}、{@code latency_p95}、{@code span_count}
     */
    private String metric = "";

    /**
     * {@code gt}、{@code lt}、{@code gte}、{@code lte}、{@code eq}
     */
    private String operator = "gte";

    /**
     * 与指标同一单位：错误率是百分比，次数是条数，延迟是毫秒
     */
    private double threshold;

    /**
     * 已配置的条件优先；没有时把旧的单个指标和阈值收成一条 {@code gte}。
     *
     * @param conditions 新条件；空或 null 表示沿用旧字段
     * @param metric     旧指标名
     * @param threshold  旧阈值
     * @return 实际参与判定的条件
     */
    public static List<AlertCondition> resolve(List<AlertCondition> conditions, String metric, double threshold) {
        if (conditions != null && !conditions.isEmpty()) {
            return List.copyOf(conditions);
        }
        AlertCondition legacy = new AlertCondition();
        legacy.setMetric(metric == null || metric.isBlank() ? "error_rate" : metric.trim().toLowerCase());
        legacy.setOperator("gte");
        legacy.setThreshold(threshold);
        List<AlertCondition> one = new ArrayList<>(1);
        one.add(legacy);
        return one;
    }
}
