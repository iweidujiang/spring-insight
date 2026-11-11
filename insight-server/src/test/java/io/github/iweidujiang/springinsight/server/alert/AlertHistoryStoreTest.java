package io.github.iweidujiang.springinsight.server.alert;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 告警历史环形缓冲。
 *
 * @author 苏渡苇
 * @since 2026-11-10
 */
class AlertHistoryStoreTest {

    /**
     * 新事件在前，超容量淘汰最旧。
     */
    @Test
    void ringKeepsNewestFirst() {
        AlertHistoryStore store = new AlertHistoryStore();
        for (int i = 0; i < AlertHistoryStore.MAX_EVENTS + 5; i++) {
            store.record(
                    AlertHistoryStore.EVENT_FIRE,
                    "svc-" + i,
                    "error_rate",
                    12.0,
                    10.0,
                    "2026-11-10T00:00:00Z",
                    1_000L + i,
                    null,
                    true,
                    List.of());
        }
        assertEquals(AlertHistoryStore.MAX_EVENTS, store.size());
        List<Map<String, Object>> recent = store.recent(3);
        assertEquals(3, recent.size());
        assertEquals("svc-" + (AlertHistoryStore.MAX_EVENTS + 4), recent.get(0).get("serviceName"));
        assertTrue(recent.get(0).get("atEpochMs") instanceof Number);
    }
}
