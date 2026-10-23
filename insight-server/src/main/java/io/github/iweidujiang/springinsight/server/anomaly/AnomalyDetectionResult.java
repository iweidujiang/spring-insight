package io.github.iweidujiang.springinsight.server.anomaly;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次异常检测结果（可序列化为告警 / UI 字段）。
 *
 * @param isAnomaly    是否判定为异常
 * @param algorithm    {@code z-score} / {@code period-over-period} / {@code insufficientData} / {@code none}
 * @param zScore       Z 分数；非 z-score 时可为 {@code null}
 * @param mean         窗口均值
 * @param std          窗口标准差
 * @param currentValue 当前指标值
 * @param message      可读说明
 * @param sampleCount  参与统计的历史点数（不含当前）
 * @author 苏渡苇
 * @since 2026-10-23
 */
public record AnomalyDetectionResult(boolean isAnomaly,
                                     String algorithm,
                                     Double zScore,
                                     Double mean,
                                     Double std,
                                     double currentValue,
                                     String message,
                                     int sampleCount) {

    /**
     * 数据不足时的结果。
     *
     * @param currentValue 当前值
     * @param sampleCount  已有点数
     * @return 结果
     */
    public static AnomalyDetectionResult insufficient(double currentValue, int sampleCount) {
        return new AnomalyDetectionResult(
                false,
                "insufficientData",
                null,
                null,
                null,
                currentValue,
                "历史样本不足（需至少 " + AnomalyDetector.MIN_SAMPLES + " 个点，当前 "
                        + sampleCount + "），暂不判定异常",
                sampleCount);
    }

    /**
     * 正常（已有足够样本且未超阈值）。
     *
     * @param currentValue 当前值
     * @param mean         均值
     * @param std          标准差
     * @param sampleCount  样本数
     * @return 结果
     */
    public static AnomalyDetectionResult normal(double currentValue,
                                                double mean,
                                                double std,
                                                int sampleCount) {
        return new AnomalyDetectionResult(
                false,
                "none",
                null,
                round4(mean),
                round4(std),
                currentValue,
                "指标处于历史正常范围",
                sampleCount);
    }

    /**
     * @return 便于写入 JSON / Map 的结构
     */
    public Map<String, Object> toMap() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("isAnomaly", isAnomaly);
        body.put("algorithm", algorithm != null ? algorithm : "");
        if (zScore != null) {
            body.put("zScore", zScore);
        }
        if (mean != null) {
            body.put("mean", mean);
        }
        if (std != null) {
            body.put("std", std);
        }
        body.put("currentValue", currentValue);
        body.put("message", message != null ? message : "");
        body.put("sampleCount", sampleCount);
        return body;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
