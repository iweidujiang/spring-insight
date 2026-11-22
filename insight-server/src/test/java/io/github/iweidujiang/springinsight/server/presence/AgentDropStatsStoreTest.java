package io.github.iweidujiang.springinsight.server.presence;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentDropStatsStore} 单测。
 *
 * @author 苏渡苇
 * @since 2026-11-22
 */
class AgentDropStatsStoreTest {

    @Test
    void recordAndGet_roundTrip() {
        AgentDropStatsStore store = new AgentDropStatsStore();
        store.record("order", 12L, 3, 100);
        AgentDropStatsStore.Entry entry = store.get("order");
        assertNotNull(entry);
        assertEquals(12L, entry.queueDropped());
        assertEquals(3, entry.queueSize());
        assertEquals(100, entry.queueCapacity());
        assertTrue(entry.updatedEpochMs() > 0L);
    }

    @Test
    void record_ignoresBlankOrEmptyFields() {
        AgentDropStatsStore store = new AgentDropStatsStore();
        store.record("  ", 1L, 1, 1);
        store.record("svc", null, null, null);
        assertNull(store.get("svc"));
        assertTrue(store.toMap("svc").isEmpty());
    }

    @Test
    void toMap_exposesApiFields() {
        AgentDropStatsStore store = new AgentDropStatsStore();
        store.record("gateway", 5L, 1, 50);
        Map<String, Object> map = store.toMap("gateway");
        assertEquals(5L, map.get("queue_dropped"));
        assertEquals(1, map.get("queue_size"));
        assertEquals(50, map.get("queue_capacity"));
        assertNotNull(map.get("updated_epoch_ms"));
    }
}
