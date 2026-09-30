package io.github.iweidujiang.springinsight.agent.boot2.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TraceSampler} Boot2 单测。
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public class TraceSamplerTest {

    /**
     * 边界：0 永不采，1 全采。
     */
    @Test
    public void decide_boundaries() {
        assertFalse(TraceSampler.decide(0.0));
        assertFalse(TraceSampler.decide(-1.0));
        assertTrue(TraceSampler.decide(1.0));
        assertTrue(TraceSampler.decide(2.0));
    }

    /**
     * clamp 将 NaN / 越界夹到 [0,1]。
     */
    @Test
    public void clamp_normalizes() {
        assertEquals(0.0, TraceSampler.clamp(Double.NaN), 0.0);
        assertEquals(0.0, TraceSampler.clamp(-0.5), 0.0);
        assertEquals(1.0, TraceSampler.clamp(1.5), 0.0);
        assertEquals(0.3, TraceSampler.clamp(0.3), 0.0);
    }
}
