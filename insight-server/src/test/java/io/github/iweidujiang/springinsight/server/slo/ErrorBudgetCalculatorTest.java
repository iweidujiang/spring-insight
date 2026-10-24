package io.github.iweidujiang.springinsight.server.slo;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误预算公式。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
class ErrorBudgetCalculatorTest {

    /**
     * 规格示例：目标 99.9%，实际 98.75%，燃烧率 12.5，状态 exhausted。
     */
    @Test
    void compute_matchesSpecExample() {
        // 错误率 1.25% → 1000 请求中 12.5≈13？用精确：error/total = 0.0125
        Map<String, Object> body = ErrorBudgetCalculator.compute(
                "sca-user", 0.999, 720, 720, 10000, 125, 1_700_000_000_000L);

        assertEquals("sca-user", body.get("service"));
        assertEquals(0.999, ((Number) body.get("target")).doubleValue(), 1e-9);
        assertEquals(0.9875, ((Number) body.get("actualAvailability")).doubleValue(), 1e-9);
        assertEquals(0.001, ((Number) body.get("budgetTotal")).doubleValue(), 1e-9);
        assertEquals(0.0125, ((Number) body.get("budgetConsumed")).doubleValue(), 1e-9);
        assertEquals(-0.0115, ((Number) body.get("budgetRemaining")).doubleValue(), 1e-9);
        assertEquals("exhausted", body.get("status"));
        assertEquals(12.5, ((Number) body.get("burnRate")).doubleValue(), 1e-6);
        assertTrue(String.valueOf(body.get("estimatedExhaustion")).endsWith("Z")
                || String.valueOf(body.get("estimatedExhaustion")).contains("T"));
    }

    /**
     * 剩余预算过半为 healthy。
     */
    @Test
    void compute_healthyWhenRemainingAboveHalf() {
        // 目标 99.9%，错误率 0.01% → consumed 0.0001，remaining 0.0009 = 90%
        Map<String, Object> body = ErrorBudgetCalculator.compute(
                "svc", 0.999, 720, 720, 100_000, 10, System.currentTimeMillis());
        assertEquals("healthy", body.get("status"));
        assertTrue(((Number) body.get("budgetRemaining")).doubleValue() > 0);
        assertTrue(((Number) body.get("burnRate")).doubleValue() < 1.0);
    }

    /**
     * 剩余约 30% 为 warning。
     */
    @Test
    void compute_warningWhenRemainingBetween10And50Percent() {
        // consumed 0.0007，remaining 0.0003 = 30%
        Map<String, Object> body = ErrorBudgetCalculator.compute(
                "svc", 0.999, 720, 720, 100_000, 70, System.currentTimeMillis());
        assertEquals("warning", body.get("status"));
        assertEquals(0.0003, ((Number) body.get("budgetRemaining")).doubleValue(), 1e-9);
    }

    /**
     * 无流量时视为 100% 可用。
     */
    @Test
    void compute_noTrafficIsFullyAvailable() {
        Map<String, Object> body = ErrorBudgetCalculator.compute(
                "empty", 0.999, 720, 24, 0, 0, System.currentTimeMillis());
        assertEquals(1.0, ((Number) body.get("actualAvailability")).doubleValue(), 1e-9);
        assertEquals(0.0, ((Number) body.get("budgetConsumed")).doubleValue(), 1e-9);
        assertEquals("healthy", body.get("status"));
        assertEquals(0.0, ((Number) body.get("burnRate")).doubleValue(), 1e-9);
    }
}
