package io.github.iweidujiang.springinsight.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import io.github.iweidujiang.springinsight.storage.impl.SqliteSpanStore;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import io.github.iweidujiang.springinsight.storage.spi.TraceSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 链路列表分页：不传 offset 时摘要与原来一致；按耗时降序只取前 10 条。
 */
class TraceListPageTest {

    @TempDir
    Path tempDir;

    @Test
    void unpagedSummaryStaysFlatAndTimeOrdered() {
        InMemorySpanStore store = new InMemorySpanStore(props(100), "memory");
        store.saveAll(sample(3));
        List<Map<String, Object>> rows = new TraceSpanPersistenceService(store).getRecentTraceSummaries(
                24, 100, "page-svc", "all", null, 0, null);
        assertEquals(3, rows.size());
        assertEquals("t3", rows.get(0).get("traceId"));
        assertFalse(rows.get(0).containsKey("data"));
    }

    @Test
    void durationDescReturnsSlowestPage() {
        InMemorySpanStore memory = new InMemorySpanStore(props(100), "memory");
        memory.saveAll(sample(12));
        assertSlowestPage(memory);

        SqliteSpanStore sqlite = sqlite(100);
        sqlite.saveAll(sample(12));
        assertSlowestPage(sqlite);
        sqlite.close();
    }

    private static void assertSlowestPage(SpanStore store) {
        assertEquals(12, store.countTraces(0L, "page-svc", "all", null, 0, null));
        List<TraceSummary> page = store.listTraces(0L, "page-svc", "all", null, 0, null, 0, 10, "duration_desc");
        assertEquals(10, page.size());
        assertEquals(120L, page.get(0).durationMs());
        assertEquals("t12", page.get(0).traceId());
        assertEquals(30L, page.get(9).durationMs());
        List<TraceSummary> rest = store.listTraces(0L, "page-svc", "all", null, 0, null, 10, 10, "duration_desc");
        assertEquals(2, rest.size());
        assertEquals(20L, rest.get(0).durationMs());
        assertEquals(10L, rest.get(1).durationMs());
    }

    private static List<TraceSpan> sample(int traces) {
        List<TraceSpan> spans = new ArrayList<>();
        long start = System.currentTimeMillis() - 60_000;
        for (int i = 1; i <= traces; i++) {
            TraceSpan span = new TraceSpan();
            span.setTraceId("t" + i);
            span.setSpanId("s" + i);
            span.setServiceName("page-svc");
            span.setOperationName("GET /page/" + i);
            span.setStartTime(start + i * 1000L);
            span.setDurationMs(i * 10L);
            span.setSuccess(true);
            spans.add(span);
        }
        return spans;
    }

    private SqliteSpanStore sqlite(int maxSpans) {
        InsightServerStorageProperties properties = props(maxSpans);
        properties.setMode("sqlite");
        properties.getSqlite().setPath(tempDir.resolve("page.db").toString());
        return new SqliteSpanStore(properties, new ObjectMapper());
    }

    private static InsightServerStorageProperties props(int maxSpans) {
        InsightServerStorageProperties properties = new InsightServerStorageProperties();
        properties.setMaxSpans(maxSpans);
        return properties;
    }
}
