package io.github.iweidujiang.springinsight.server.jvm;

import io.github.iweidujiang.springinsight.agent.model.JvmMetric;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进程内 JVM 指标环形缓冲单测。
 *
 * @author 苏渡苇
 * @since 2026-11-09
 */
class InMemoryJvmMetricStoreTest {

    @Test
    void saveAndQueryByServiceAndInstance() {
        InMemoryJvmMetricStore store = new InMemoryJvmMetricStore();
        long now = System.currentTimeMillis();

        JvmMetric a1 = point("demo", "i1", now - 60_000, 10_000_000L, 5);
        JvmMetric a2 = point("demo", "i1", now - 30_000, 12_000_000L, 6);
        JvmMetric b1 = point("demo", "i2", now - 20_000, 8_000_000L, 4);
        store.saveAll("demo", "i1", List.of(a1, a2));
        store.saveAll("demo", "i2", List.of(b1));

        assertEquals(List.of("i1", "i2"), store.listInstances("demo"));
        assertEquals(2, store.query("demo", "i1", now - 120_000, now).size());
        assertEquals(3, store.query("demo", "", now - 120_000, now).size());
        assertEquals(12_000_000L, store.latest("demo", "i1").orElseThrow().getHeapMemoryUsed());
        assertTrue(store.latest("missing", "").isEmpty());
    }

    private static JvmMetric point(String service, String instance, long ts, long heap, int threads) {
        JvmMetric m = new JvmMetric();
        m.setServiceName(service);
        m.setServiceInstance(instance);
        m.setTimestamp(ts);
        m.setHeapMemoryUsed(heap);
        m.setHeapMemoryMax(heap * 2);
        m.setActiveThreads(threads);
        m.setGcCount(1);
        return m;
    }
}
