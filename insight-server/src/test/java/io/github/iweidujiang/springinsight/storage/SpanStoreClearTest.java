package io.github.iweidujiang.springinsight.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.FileSpanStore;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import io.github.iweidujiang.springinsight.storage.impl.SqliteSpanStore;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SpanStore 清除：全部 / 按时间 / 按服务。
 *
 * @since 2026-09-20
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class SpanStoreClearTest {

    @TempDir
    Path tempDir;

    /**
     * memory：三种清除语义。
     */
    @Test
    void memoryClearScopes() {
        InsightServerStorageProperties props = new InsightServerStorageProperties();
        props.setMaxSpans(1000);
        InMemorySpanStore store = new InMemorySpanStore(props, "memory");
        seed(store);
        assertEquals(1, store.purgeByService("svc-a"));
        assertEquals(2, store.size());
        assertEquals(1, store.purgeOlderThan(System.currentTimeMillis() - 3_600_000L));
        assertEquals(1, store.size());
        assertEquals(1, store.clearAll());
        assertEquals(0, store.size());
    }

    /**
     * file：clearAll 后磁盘为空列表可读。
     */
    @Test
    void fileClearAllFlushes() {
        InsightServerStorageProperties props = new InsightServerStorageProperties();
        props.setMode("file");
        props.setMaxSpans(1000);
        props.setFilePath(tempDir.resolve("spans.json").toString());
        props.setFlushDelayMs(50);
        FileSpanStore store = new FileSpanStore(props, new ObjectMapper());
        seed(store);
        assertEquals(3, store.clearAll());
        assertEquals(0, store.size());
        store.close();
        FileSpanStore reopened = new FileSpanStore(props, new ObjectMapper());
        assertEquals(0, reopened.size());
        reopened.close();
    }

    /**
     * sqlite：按服务与清空。
     */
    @Test
    void sqliteClearScopes() {
        InsightServerStorageProperties props = new InsightServerStorageProperties();
        props.setMode("sqlite");
        props.setMaxSpans(1000);
        props.getSqlite().setPath(tempDir.resolve("clear-test.db").toString());
        SqliteSpanStore store = new SqliteSpanStore(props, new ObjectMapper());
        seed(store);
        assertEquals(1, store.purgeByService("svc-a"));
        assertEquals(2, store.size());
        assertEquals(2, store.clearAll());
        assertEquals(0, store.size());
        store.close();
    }

    private static void seed(SpanStore store) {
        long now = System.currentTimeMillis();
        TraceSpan a = span("svc-a", now);
        TraceSpan b = span("svc-b", now);
        TraceSpan old = span("svc-b", now - 7_200_000L);
        store.saveAll(List.of(a, b, old));
        assertEquals(3, store.size());
    }

    private static TraceSpan span(String service, long start) {
        TraceSpan s = new TraceSpan();
        s.setTraceId("t-" + service + "-" + start);
        s.setSpanId("s-" + start + "-" + service.hashCode());
        s.setServiceName(service);
        s.setSuccess(true);
        s.setStatusCode("OK");
        s.setStartTime(start);
        s.setEndTime(start + 10);
        s.setDurationMs(10L);
        return s;
    }
}
