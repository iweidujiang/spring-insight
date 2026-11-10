package io.github.iweidujiang.springinsight.server.presence;

import io.github.iweidujiang.springinsight.server.config.InsightServerPresenceProperties;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 服务存活判定边界测试。
 *
 * @author 苏渡苇
 * @since 2026-11-10
 */
class ServicePresenceServiceTest {

    private TraceSpanPersistenceService persistence;
    private InsightServerPresenceProperties properties;
    private ServicePresenceService service;

    @BeforeEach
    void setUp() {
        persistence = mock(TraceSpanPersistenceService.class);
        properties = new InsightServerPresenceProperties();
        properties.setStaleThresholdMinutes(5);
        service = new ServicePresenceService(persistence, properties);
    }

    /**
     * 阈值内为 online，刚过阈值为 stale，无时间为 unknown。
     */
    @Test
    void classifyOnlineStaleUnknown() {
        long now = 1_700_000_000_000L;
        long fiveMin = 5L * 60_000L;
        assertEquals(ServicePresenceService.ONLINE, service.classify(now, now));
        assertEquals(ServicePresenceService.ONLINE, service.classify(now - fiveMin, now));
        assertEquals(ServicePresenceService.STALE, service.classify(now - fiveMin - 1L, now));
        assertEquals(ServicePresenceService.UNKNOWN, service.classify(null, now));
        assertEquals(ServicePresenceService.UNKNOWN, service.classify(0L, now));
    }

    /**
     * listPresence 带上 quiet_minutes 与阈值。
     */
    @Test
    void listPresenceIncludesQuietMinutes() {
        long now = System.currentTimeMillis();
        Map<String, Long> activity = new HashMap<>();
        activity.put("fresh-svc", now - 60_000L);
        activity.put("quiet-svc", now - 20L * 60_000L);
        when(persistence.getLastActivityEpochMsByService()).thenReturn(activity);

        List<Map<String, Object>> rows = service.listPresence();
        assertEquals(2, rows.size());

        Map<String, Object> fresh = rows.stream()
                .filter(r -> "fresh-svc".equals(r.get("service_name")))
                .findFirst()
                .orElseThrow();
        assertEquals(ServicePresenceService.ONLINE, fresh.get("presence"));
        assertEquals(5, fresh.get("stale_threshold_minutes"));
        assertEquals(1L, ((Number) fresh.get("quiet_minutes")).longValue());

        Map<String, Object> quiet = rows.stream()
                .filter(r -> "quiet-svc".equals(r.get("service_name")))
                .findFirst()
                .orElseThrow();
        assertEquals(ServicePresenceService.STALE, quiet.get("presence"));
        assertEquals(20L, ((Number) quiet.get("quiet_minutes")).longValue());
    }

    /**
     * attachPresence 原地补充字段，未知服务为 unknown。
     */
    @Test
    void attachPresenceOnLatencyRows() {
        when(persistence.getLastActivityEpochMsByService()).thenReturn(Map.of(
                "svc-a", System.currentTimeMillis()));

        Map<String, Object> row = new HashMap<>();
        row.put("service_name", "svc-a");
        row.put("p95_ms", 12L);
        Map<String, Object> ghost = new HashMap<>();
        ghost.put("service_name", "ghost");
        ghost.put("p95_ms", 1L);

        service.attachPresence(List.of(row, ghost));
        assertEquals(ServicePresenceService.ONLINE, row.get("presence"));
        assertEquals(ServicePresenceService.UNKNOWN, ghost.get("presence"));
        assertNull(ghost.get("last_activity_epoch_ms"));
        assertEquals(12L, row.get("p95_ms"));
    }
}
