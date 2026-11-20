package io.github.iweidujiang.springinsight.server.presence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 心跳缓存单测。
 *
 * @author 苏渡苇
 * @since 2026-11-20
 */
class AgentHeartbeatStoreTest {

    @Test
    void record_keepsLatestPerService() {
        AgentHeartbeatStore store = new AgentHeartbeatStore();
        store.record("demo", 1_000L);
        store.record("demo", 3_000L);
        store.record("demo", 2_000L);
        assertEquals(3_000L, store.getLastHeartbeatEpochMs("demo").orElse(-1L));
    }

    @Test
    void record_ignoresBlankService() {
        AgentHeartbeatStore store = new AgentHeartbeatStore();
        store.record("  ", 1_000L);
        assertFalse(store.getLastHeartbeatEpochMs(" ").isPresent());
        assertTrue(store.snapshot().isEmpty());
    }
}
