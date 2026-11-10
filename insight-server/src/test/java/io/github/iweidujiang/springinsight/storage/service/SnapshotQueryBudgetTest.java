package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.SnapshotQueryTooLargeException;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全量快照查询预算：超限拒绝重查询，延迟改走聚合。
 *
 * @author 苏渡苇
 * @since 2026-11-10
 */
class SnapshotQueryBudgetTest {

    private InsightServerStorageProperties props;
    private InMemorySpanStore store;
    private TraceSpanPersistenceService persistence;

    @BeforeEach
    void setUp() {
        props = new InsightServerStorageProperties();
        props.setMaxSpans(1000);
        props.setSnapshotQueryMaxSpans(5);
        store = new InMemorySpanStore(props, "memory");
        persistence = new TraceSpanPersistenceService(store, props);
    }

    /**
     * 持有量超过预算时依赖查询抛 QUERY_TOO_LARGE。
     */
    @Test
    void dependenciesRejectWhenOverBudget() {
        seedSpans(6);
        assertTrue(persistence.isOverSnapshotBudget());
        SnapshotQueryTooLargeException ex = assertThrows(
                SnapshotQueryTooLargeException.class,
                () -> persistence.getServiceDependencies(24));
        assertEquals(6, ex.getStoredSpans());
        assertEquals(5, ex.getSnapshotQueryMaxSpans());
        assertTrue(ex.getMessage().contains("超过全量查询上限"));
    }

    /**
     * 超预算时延迟摘要仍可用（聚合路径）。
     */
    @Test
    void latencyUsesAggregateWhenOverBudget() {
        seedSpans(6);
        List<Map<String, Object>> rows = persistence.getServiceLatencySummaries(24, 20);
        assertFalse(rows.isEmpty());
        assertEquals("svc-a", rows.get(0).get("service_name"));
        assertTrue(((Number) rows.get(0).get("span_count")).longValue() >= 1L);
    }

    /**
     * 预算为 0 时不限制。
     */
    @Test
    void zeroBudgetDisablesGuard() {
        props.setSnapshotQueryMaxSpans(0);
        seedSpans(6);
        assertFalse(persistence.isOverSnapshotBudget());
        persistence.getServiceDependencies(24);
    }

    private void seedSpans(int n) {
        List<TraceSpan> batch = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            TraceSpan s = new TraceSpan();
            s.setTraceId("t-" + i);
            s.setSpanId("s-" + i);
            s.setServiceName("svc-a");
            s.setRemoteService("svc-b");
            s.setSpanKind("CLIENT");
            s.setStartTime(now - i * 1000L);
            s.setDurationMs(10L + i);
            s.setSuccess(true);
            batch.add(s);
        }
        store.saveAll(batch);
    }
}
