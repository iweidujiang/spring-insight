package io.github.iweidujiang.springinsight.agent.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TraceSampler} 单测。
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class TraceSamplerTest {

    /**
     * 边界：0 永不采，1 全采。
     */
    @Test
    void decide_boundaries() {
        assertFalse(TraceSampler.decide(0.0));
        assertFalse(TraceSampler.decide(-1.0));
        assertTrue(TraceSampler.decide(1.0));
        assertTrue(TraceSampler.decide(2.0));
    }

    /**
     * clamp 将 NaN / 越界夹到 [0,1]。
     */
    @Test
    void clamp_normalizes() {
        assertEquals(0.0, TraceSampler.clamp(Double.NaN));
        assertEquals(0.0, TraceSampler.clamp(-0.5));
        assertEquals(1.0, TraceSampler.clamp(1.5));
        assertEquals(0.3, TraceSampler.clamp(0.3));
    }
}
