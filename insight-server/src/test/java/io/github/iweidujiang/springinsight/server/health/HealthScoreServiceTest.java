package io.github.iweidujiang.springinsight.server.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 健康分：错误率插值、延迟阶梯、分级边界与无数据。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
class HealthScoreServiceTest {

    private final HealthScoreService service = new HealthScoreService(null);

    @Test
    void errorScoreAtAnchors() {
        assertEquals(100.0, HealthScoreService.errorScore(0.0), 0.001);
        assertEquals(80.0, HealthScoreService.errorScore(1.0), 0.001);
        assertEquals(40.0, HealthScoreService.errorScore(5.0), 0.001);
        assertEquals(0.0, HealthScoreService.errorScore(10.0), 0.001);
        assertEquals(0.0, HealthScoreService.errorScore(25.0), 0.001);
    }

    @Test
    void errorScoreLinearInterpolation() {
        assertEquals(90.0, HealthScoreService.errorScore(0.5), 0.001);
        assertEquals(60.0, HealthScoreService.errorScore(3.0), 0.001);
        assertEquals(20.0, HealthScoreService.errorScore(7.5), 0.001);
    }

    @Test
    void latencyScoreSteps() {
        assertEquals(100.0, HealthScoreService.latencyScore(0L), 0.001);
        assertEquals(100.0, HealthScoreService.latencyScore(99L), 0.001);
        assertEquals(70.0, HealthScoreService.latencyScore(100L), 0.001);
        assertEquals(70.0, HealthScoreService.latencyScore(499L), 0.001);
        assertEquals(40.0, HealthScoreService.latencyScore(500L), 0.001);
        assertEquals(40.0, HealthScoreService.latencyScore(999L), 0.001);
        assertEquals(0.0, HealthScoreService.latencyScore(1000L), 0.001);
    }

    @Test
    void levelBoundaries() {
        assertEquals("healthy", HealthScoreService.levelOf(100));
        assertEquals("healthy", HealthScoreService.levelOf(80));
        assertEquals("warning", HealthScoreService.levelOf(79));
        assertEquals("warning", HealthScoreService.levelOf(60));
        assertEquals("degraded", HealthScoreService.levelOf(59));
        assertEquals("degraded", HealthScoreService.levelOf(40));
        assertEquals("critical", HealthScoreService.levelOf(39));
        assertEquals("critical", HealthScoreService.levelOf(0));
    }

    @Test
    void noDataReturnsUnknown() {
        HealthScoreService.HealthScore hs = service.score("ghost-svc", 0.0, 0L, false);
        assertFalse(hs.hasData());
        assertEquals("unknown", hs.level());
        assertEquals("ghost-svc", hs.service());
        // 0*0.5 + 0*0.3 + 20*0.2 = 4
        assertEquals(4, hs.score());
    }

    @Test
    void perfectServiceIsHealthy() {
        // 100*0.5 + 100*0.3 + 100*0.2 = 100
        HealthScoreService.HealthScore hs = service.score("ok-svc", 0.0, 50L, true);
        assertTrue(hs.hasData());
        assertEquals(100, hs.score());
        assertEquals("healthy", hs.level());
        assertEquals(0.0, hs.errorRate(), 0.001);
        assertEquals(50L, hs.p95());
    }

    @Test
    void highErrorAndSlowIsCritical() {
        // error 10%→0, p95 1s→0, avail 100 → 20
        HealthScoreService.HealthScore hs = service.score("bad-svc", 10.0, 1000L, true);
        assertEquals(20, hs.score());
        assertEquals("critical", hs.level());
    }

    @Test
    void weightedScoreMatchesFormula() {
        // err 1%→80, p95 200→70, avail 100 → 40+21+20 = 81
        HealthScoreService.HealthScore hs = service.score("mid-svc", 1.0, 200L, true);
        assertEquals(81, hs.score());
        assertEquals("healthy", hs.level());
    }
}
