package io.github.iweidujiang.springinsight.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.SqliteSpanStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SqliteSpanStore 冒烟：写入、按 Trace 查询、重启等价（重新打开库）。
 *
 * @since 2026-09-13
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class SqliteSpanStoreTest {

    @TempDir
    Path tempDir;

    private InsightServerStorageProperties properties;
    private SqliteSpanStore store;

    @BeforeEach
    void setUp() {
        properties = new InsightServerStorageProperties();
        properties.setMode("sqlite");
        properties.setMaxSpans(1000);
        properties.getSqlite().setPath(tempDir.resolve("insight-test.db").toString());
        store = new SqliteSpanStore(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    void saveAndFindByTraceId() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        TraceSpan span = new TraceSpan();
        span.setTraceId(traceId);
        span.setSpanId("s1");
        span.setServiceName("demo");
        span.setStartTime(System.currentTimeMillis());
        span.setSuccess(true);

        assertEquals(1, store.saveAll(List.of(span)));
        assertEquals(1, store.size());
        List<TraceSpan> found = store.findByTraceId(traceId);
        assertEquals(1, found.size());
        assertEquals("demo", found.get(0).getServiceName());
    }

    @Test
    void reopenDatabaseKeepsData() {
        String traceId = "trace-reopen";
        TraceSpan span = new TraceSpan();
        span.setTraceId(traceId);
        span.setSpanId("s1");
        span.setServiceName("demo");
        span.setStartTime(System.currentTimeMillis());
        store.saveAll(List.of(span));
        store.close();

        store = new SqliteSpanStore(properties, new ObjectMapper());
        assertFalse(store.findByTraceId(traceId).isEmpty());
        assertEquals(1, store.size());
    }

    @Test
    void createsCompositeIndexes() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Set<String> names = new HashSet<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + properties.getSqlite().getPath());
             ResultSet rs = c.createStatement().executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'index'")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        assertTrue(names.contains("idx_span_start_service"));
        assertTrue(names.contains("idx_span_start_error"));
        assertTrue(names.contains("idx_span_start_component"));
    }
}
