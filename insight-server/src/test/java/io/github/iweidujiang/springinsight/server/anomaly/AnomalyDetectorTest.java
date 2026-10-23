package io.github.iweidujiang.springinsight.server.anomaly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异常检测：样本不足、正常波动、突变。
 *
 * @author 苏渡苇
 * @since 2026-10-23
 */
class AnomalyDetectorTest {

    private AnomalyDetector detector;

    /**
     * 每测独立窗口。
     */
    @BeforeEach
    void setUp() {
        detector = new AnomalyDetector();
    }

    /**
     * 不足 10 个点时返回 insufficientData，且不判异常。
     */
    @Test
    void detect_insufficientDataWhenFewerThanTen() {
        for (int i = 0; i < 5; i++) {
            AnomalyDetectionResult r = detector.detect("error_rate", "sca-user", 2.0);
            assertFalse(r.isAnomaly());
            assertEquals("insufficientData", r.algorithm());
        }
        AnomalyDetectionResult last = detector.detect("error_rate", "sca-user", 9.0);
        assertFalse(last.isAnomaly());
        assertEquals("insufficientData", last.algorithm());
        assertEquals(5, last.sampleCount());
        assertEquals(6, detector.windowSize("error_rate", "sca-user"));
    }

    /**
     * 稳定序列后接近均值的点不触发。
     */
    @Test
    void detect_normalDataDoesNotFire() {
        seedStableWindow("error_rate", "sca-user", 2.0, 0.05);
        AnomalyDetectionResult r = detector.detect("error_rate", "sca-user", 2.05);
        assertFalse(r.isAnomaly());
        assertTrue("none".equals(r.algorithm()) || "insufficientData".equals(r.algorithm()));
        assertEquals("none", r.algorithm());
        assertTrue(r.mean() != null && r.mean() > 1.5 && r.mean() < 2.5);
    }

    /**
     * 突变点触发 Z-score 异常。
     */
    @Test
    void detect_spikeTriggersZScore() {
        seedStableWindow("error_rate", "sca-user", 2.1, 0.05);
        AnomalyDetectionResult r = detector.detect("error_rate", "sca-user", 5.0);
        assertTrue(r.isAnomaly());
        assertEquals("z-score", r.algorithm());
        assertTrue(r.zScore() != null && Math.abs(r.zScore()) > 3.0);
        assertEquals(5.0, r.currentValue(), 0.001);
        assertTrue(r.message().contains("错误率"));
        assertTrue(r.message().contains("Z="));
        assertTrue(Boolean.TRUE.equals(r.toMap().get("isAnomaly")));
    }

    /**
     * 方差近零时的阶跃由环比检出。
     */
    @Test
    void detect_flatThenJumpUsesPeriodOverPeriod() {
        for (int i = 0; i < AnomalyDetector.MIN_SAMPLES; i++) {
            detector.detect("span_count", "sca-order", 100.0);
        }
        AnomalyDetectionResult r = detector.detect("span_count", "sca-order", 200.0);
        assertTrue(r.isAnomaly());
        assertEquals("period-over-period", r.algorithm());
        assertTrue(r.message().contains("变化"));
    }

    /**
     * 窗口最多保留 100 点。
     */
    @Test
    void detect_trimsWindowToMax() {
        for (int i = 0; i < AnomalyDetector.MAX_WINDOW + 25; i++) {
            detector.detect("error_count", "svc", 1.0);
        }
        assertEquals(AnomalyDetector.MAX_WINDOW, detector.windowSize("error_count", "svc"));
    }

    private void seedStableWindow(String metric, String service, double center, double wobble) {
        for (int i = 0; i < AnomalyDetector.MIN_SAMPLES + 5; i++) {
            double v = center + ((i % 3) - 1) * wobble;
            detector.detect(metric, service, v);
        }
    }
}
