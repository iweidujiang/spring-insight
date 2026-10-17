package io.github.iweidujiang.springinsight.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import io.github.iweidujiang.springinsight.storage.impl.SqliteSpanStore;
import io.github.iweidujiang.springinsight.storage.spi.ServiceAggregate;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL / 内存聚合应与基于 snapshot 的结果一致，且大批量时不必把 Span 快照留在堆上。
 */
class SpanStoreAggregateTest {

    @TempDir
    Path tempDir;

    @Test
    void memoryAggregateMatchesSnapshotWalk() {
        InMemorySpanStore store = new InMemorySpanStore(props(100), "memory");
        store.saveAll(sample());
        assertSameAggregates(store, 0L, null);
        List<ServiceAggregate> onlyDemo = store.aggregateSnapshot(0L, "demo");
        assertEquals(1, onlyDemo.size());
        assertEquals("demo", onlyDemo.get(0).serviceName());
        assertEquals(2, onlyDemo.get(0).spanCount());
        assertEquals(1, onlyDemo.get(0).errorCount());
    }

    @Test
    void sqliteAggregateMatchesSnapshotWalk() {
        SqliteSpanStore store = sqlite(100);
        try {
            store.saveAll(sample());
            assertSameAggregates(store, 0L, null);
            ServiceAggregate demo = store.aggregateSnapshot(0L, "demo").get(0);
            assertEquals(100L, demo.maxDuration());
            assertEquals(1, demo.errorCount());
            assertTrue(demo.p95Duration() >= demo.p50Duration());
        } finally {
            store.close();
        }
    }

    @Test
    void sqliteAggregateRetainsFarLessThanSnapshot() {
        int count = 50_000;
        SqliteSpanStore store = sqlite(count + 10);
        try {
            List<TraceSpan> batch = new ArrayList<>(count);
            long start = System.currentTimeMillis() - 60_000;
            for (int i = 0; i < count; i++) {
                TraceSpan span = new TraceSpan();
                span.setTraceId("t" + (i % 1000));
                span.setSpanId("s" + i);
                span.setServiceName("svc-" + (i % 8));
                span.setStartTime(start + i);
                span.setDurationMs(10L + (i % 400));
                span.setSuccess(i % 17 != 0);
                batch.add(span);
            }
            store.saveAll(batch);

            long baseline = heapAfterGc();
            List<TraceSpan> snapshot = store.snapshot();
            assertEquals(count, snapshot.size());
            long snapshotExtra = heapAfterGc() - baseline;
            snapshot = null;
            heapAfterGc();

            List<ServiceAggregate> aggregated = store.aggregateSnapshot(0L, null);
            long aggregateExtra = Math.max(0L, heapAfterGc() - baseline);
            assertEquals(8, aggregated.size());
            long total = aggregated.stream().mapToLong(ServiceAggregate::spanCount).sum();
            assertEquals(count, total);
            assertTrue(snapshotExtra > 8L * 1024 * 1024,
                    "snapshot should retain a large copy, extra=" + snapshotExtra);
            assertTrue(aggregateExtra * 5 < snapshotExtra,
                    "aggregate extra " + aggregateExtra + " should stay well below snapshot extra " + snapshotExtra);
        } finally {
            store.close();
        }
    }

    private static void assertSameAggregates(SpanStore store, long since, String service) {
        List<ServiceAggregate> fromStore = store.aggregateSnapshot(since, service);
        List<ServiceAggregate> fromSnapshot = walk(store.snapshot(), since, service);
        assertEquals(fromSnapshot.size(), fromStore.size());
        for (int i = 0; i < fromStore.size(); i++) {
            ServiceAggregate expected = fromSnapshot.get(i);
            ServiceAggregate actual = fromStore.get(i);
            assertEquals(expected.serviceName(), actual.serviceName());
            assertEquals(expected.spanCount(), actual.spanCount());
            assertEquals(expected.errorCount(), actual.errorCount());
            assertEquals(expected.avgDuration(), actual.avgDuration(), 0.01);
            assertEquals(expected.maxDuration(), actual.maxDuration());
            assertEquals(expected.p50Duration(), actual.p50Duration());
            assertEquals(expected.p95Duration(), actual.p95Duration());
        }
    }

    /**
     * 对照：先 snapshot 再在内存里按服务汇总。分位数算法与存储实现相同。
     */
    private static List<ServiceAggregate> walk(List<TraceSpan> spans, long since, String service) {
        return io.github.iweidujiang.springinsight.storage.impl.ServiceAggregateCalculator.fromSpans(spans, since, service);
    }

    private static List<TraceSpan> sample() {
        TraceSpan slow = span("demo", "a", 100, false);
        TraceSpan fast = span("demo", "b", 40, true);
        TraceSpan other = span("pay", "c", 20, true);
        return List.of(slow, fast, other);
    }

    private static TraceSpan span(String service, String spanId, long duration, boolean success) {
        TraceSpan span = new TraceSpan();
        span.setTraceId("trace-1");
        span.setSpanId(spanId);
        span.setServiceName(service);
        span.setStartTime(System.currentTimeMillis());
        span.setDurationMs(duration);
        span.setSuccess(success);
        if (!success) {
            span.setStatusCode("ERROR");
        }
        return span;
    }

    private SqliteSpanStore sqlite(int maxSpans) {
        InsightServerStorageProperties properties = props(maxSpans);
        properties.setMode("sqlite");
        properties.getSqlite().setPath(tempDir.resolve("agg-" + maxSpans + ".db").toString());
        return new SqliteSpanStore(properties, new ObjectMapper());
    }

    private static InsightServerStorageProperties props(int maxSpans) {
        InsightServerStorageProperties properties = new InsightServerStorageProperties();
        properties.setMaxSpans(maxSpans);
        return properties;
    }

    private static long heapAfterGc() {
        for (int i = 0; i < 4; i++) {
            System.gc();
        }
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
