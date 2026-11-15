package io.github.iweidujiang.springinsight.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 观测时间窗解析。
 *
 * @author 苏渡苇
 * @since 2026-11-15
 */
class QueryWindowTest {

    /**
     * 绝对 from/to 优先于 hours。
     */
    @Test
    void absoluteWinsOverHours() {
        QueryWindow w = QueryWindow.resolve(72, 1_000L, 2_000L);
        assertTrue(w.isAbsolute());
        assertEquals(1_000L, w.fromInclusiveMs());
        assertEquals(2_000L, w.toExclusiveMs());
        assertTrue(w.contains(1_000L));
        assertTrue(w.contains(1_999L));
        assertFalse(w.contains(2_000L));
        assertFalse(w.contains(999L));
    }

    /**
     * 滚动窗仅设下界。
     */
    @Test
    void rollingHoursLowerBoundOnly() {
        QueryWindow w = QueryWindow.ofHours(1);
        assertFalse(w.isAbsolute());
        assertEquals(1, w.hoursEcho());
        assertTrue(w.fromInclusiveMs() > 0L);
        assertEquals(0L, w.toExclusiveMs());
        assertTrue(w.contains(System.currentTimeMillis()));
    }

    /**
     * hours=0 表示全部已存。
     */
    @Test
    void allStoredOpenBounds() {
        QueryWindow w = QueryWindow.ofHours(0);
        assertEquals(0, w.hoursEcho());
        assertEquals(0L, w.fromInclusiveMs());
        assertEquals(0L, w.toExclusiveMs());
        assertTrue(w.contains(0L));
    }
}
