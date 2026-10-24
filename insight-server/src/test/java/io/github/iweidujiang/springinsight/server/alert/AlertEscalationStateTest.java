package io.github.iweidujiang.springinsight.server.alert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 活跃告警升级判定。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
class AlertEscalationStateTest {

    /**
     * 未满阈值不升级；到达阈值后升级；已升级不再触发。
     */
    @Test
    void shouldEscalate_onceAfterThreshold() {
        long t0 = 1_700_000_000_000L;
        AlertEscalationState open = AlertEscalationState.open(t0);
        assertFalse(open.shouldEscalate(t0 + 29 * 60_000L, 30));
        assertTrue(open.shouldEscalate(t0 + 30 * 60_000L, 30));
        assertEquals(30L, open.durationMinutes(t0 + 30 * 60_000L));

        AlertEscalationState done = open.markEscalated();
        assertTrue(done.escalated());
        assertFalse(done.shouldEscalate(t0 + 60 * 60_000L, 30));
    }
}
