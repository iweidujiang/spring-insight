package io.github.iweidujiang.springinsight.storage.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.spi.ServiceAggregate;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * sqlite 模式：JSON 行存 + 索引，重启可恢复。
 *
 * @since 2026-09-13
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
public class SqliteSpanStore implements SpanStore {

    private final InsightServerStorageProperties properties;
    private final ObjectMapper objectMapper;
    private final Connection connection;
    private final Object lock = new Object();
    /** 因上限/时间裁剪累计丢弃条数 */
    private final AtomicLong evictedTotal = new AtomicLong();

    /**
     * @param properties   存储配置
     * @param objectMapper 序列化 Span
     */
    public SqliteSpanStore(InsightServerStorageProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        try {
            Path dbPath = Path.of(properties.getSqlite().getPath()).toAbsolutePath().normalize();
            Path parent = dbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // 确保驱动已加载
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initSchema();
            log.info("[存储] 模式=sqlite，路径={}，maxSpans={}，当前 {} 条",
                    dbPath, properties.getMaxSpans(), size());
        } catch (Exception e) {
            throw new IllegalStateException("初始化 SQLite 存储失败: " + e.getMessage(), e);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS spans (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      trace_id TEXT NOT NULL,
                      span_id TEXT NOT NULL,
                      service_name TEXT,
                      start_time INTEGER NOT NULL DEFAULT 0,
                      payload TEXT NOT NULL,
                      error INTEGER NOT NULL DEFAULT 0,
                      component TEXT,
                      duration_ms INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            addColumnIfMissing(st, "ALTER TABLE spans ADD COLUMN error INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(st, "ALTER TABLE spans ADD COLUMN component TEXT");
            addColumnIfMissing(st, "ALTER TABLE spans ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0");
            st.execute("CREATE INDEX IF NOT EXISTS idx_spans_trace ON spans(trace_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_spans_start ON spans(start_time)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_spans_service ON spans(service_name)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_span_start_service ON spans(start_time, service_name)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_span_start_error ON spans(start_time, error)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_span_start_component ON spans(start_time, component)");
        }
    }

    /**
     * 已有库的建表语句不会补列；重复添加时忽略。
     */
    private static void addColumnIfMissing(Statement st, String ddl) throws SQLException {
        try {
            st.execute(ddl);
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (!msg.contains("duplicate column")) {
                throw e;
            }
        }
    }

    @Override
    public String mode() {
        return "sqlite";
    }

    @Override
    public int saveAll(List<TraceSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return 0;
        }
        synchronized (lock) {
            int added = 0;
            try {
                connection.setAutoCommit(false);
                String sql = """
                        INSERT INTO spans(trace_id, span_id, service_name, start_time, payload, error, component, duration_ms)
                        VALUES (?,?,?,?,?,?,?,?)
                        """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    for (TraceSpan span : spans) {
                        if (span == null || span.getTraceId() == null || span.getSpanId() == null) {
                            continue;
                        }
                        TraceSpan copy = TraceSpan.snapshot(span);
                        ps.setString(1, copy.getTraceId());
                        ps.setString(2, copy.getSpanId());
                        ps.setString(3, copy.getServiceName());
                        ps.setLong(4, n(copy.getStartTime()));
                        ps.setString(5, objectMapper.writeValueAsString(copy));
                        ps.setInt(6, ServiceAggregateCalculator.isError(copy) ? 1 : 0);
                        ps.setString(7, copy.getComponent());
                        ps.setLong(8, ServiceAggregateCalculator.durationOf(copy));
                        ps.addBatch();
                        added++;
                    }
                    ps.executeBatch();
                }
                connection.commit();
                evictByCount();
                return added;
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // ignore
                }
                throw new IllegalStateException("SQLite 写入失败: " + e.getMessage(), e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // ignore
                }
            }
        }
    }

    private void evictByCount() throws SQLException {
        int max = Math.max(1, properties.getMaxSpans());
        int current = sizeUnlocked();
        if (current <= max) {
            return;
        }
        int overflow = current - max;
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM spans WHERE id IN (SELECT id FROM spans ORDER BY start_time ASC, id ASC LIMIT ?)")) {
            ps.setInt(1, overflow);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                evictedTotal.addAndGet(deleted);
            }
        }
    }

    @Override
    public List<TraceSpan> findByTraceId(String traceId) {
        synchronized (lock) {
            List<TraceSpan> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT payload FROM spans WHERE trace_id = ? ORDER BY start_time ASC")) {
                ps.setString(1, traceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readPayload(rs.getString(1)));
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("SQLite 查询失败: " + e.getMessage(), e);
            }
            return out;
        }
    }

    @Override
    public List<TraceSpan> findRecent(int lastHours, int limit) {
        long since = sinceEpochMillis(lastHours);
        int max = Math.max(1, limit);
        synchronized (lock) {
            List<TraceSpan> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT payload FROM spans WHERE start_time >= ? ORDER BY start_time DESC LIMIT ?")) {
                ps.setLong(1, since);
                ps.setInt(2, max);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readPayload(rs.getString(1)));
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("SQLite 查询失败: " + e.getMessage(), e);
            }
            return out;
        }
    }

    @Override
    public List<TraceSpan> findByService(String serviceName, int limit) {
        int max = Math.max(1, limit);
        synchronized (lock) {
            List<TraceSpan> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT payload FROM spans WHERE service_name = ? ORDER BY start_time DESC LIMIT ?")) {
                ps.setString(1, serviceName);
                ps.setInt(2, max);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readPayload(rs.getString(1)));
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("SQLite 查询失败: " + e.getMessage(), e);
            }
            return out;
        }
    }

    @Override
    public List<TraceSpan> snapshot() {
        synchronized (lock) {
            List<TraceSpan> out = new ArrayList<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT payload FROM spans ORDER BY start_time ASC, id ASC")) {
                while (rs.next()) {
                    out.add(readPayload(rs.getString(1)));
                }
            } catch (Exception e) {
                throw new IllegalStateException("SQLite snapshot 失败: " + e.getMessage(), e);
            }
            return out;
        }
    }

    @Override
    public List<ServiceAggregate> aggregateSnapshot(long sinceEpoch, String service) {
        String filter = service == null ? "" : service.trim();
        synchronized (lock) {
            Map<String, long[]> stats = new LinkedHashMap<>();
            String summarySql = """
                    SELECT COALESCE(service_name, ''),
                           COUNT(*),
                           SUM(CASE WHEN error != 0 THEN 1 ELSE 0 END),
                           COALESCE(SUM(duration_ms), 0),
                           COALESCE(MAX(duration_ms), 0)
                    FROM spans
                    WHERE start_time >= ?
                    """ + (filter.isEmpty() ? "" : " AND service_name = ? ") + """
                    GROUP BY COALESCE(service_name, '')
                    """;
            try (PreparedStatement ps = connection.prepareStatement(summarySql)) {
                ps.setLong(1, Math.max(0L, sinceEpoch));
                if (!filter.isEmpty()) {
                    ps.setString(2, filter);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        stats.put(rs.getString(1), new long[]{
                                rs.getLong(2),
                                rs.getLong(3),
                                rs.getLong(4),
                                rs.getLong(5)
                        });
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("SQLite 聚合失败: " + e.getMessage(), e);
            }

            Map<String, List<Long>> durations = new LinkedHashMap<>();
            String durationSql = """
                    SELECT COALESCE(service_name, ''), duration_ms
                    FROM spans
                    WHERE start_time >= ?
                    """ + (filter.isEmpty() ? "" : " AND service_name = ? ") + """
                    ORDER BY 1, duration_ms
                    """;
            try (PreparedStatement ps = connection.prepareStatement(durationSql)) {
                ps.setLong(1, Math.max(0L, sinceEpoch));
                if (!filter.isEmpty()) {
                    ps.setString(2, filter);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        durations.computeIfAbsent(rs.getString(1), key -> new ArrayList<>()).add(rs.getLong(2));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("SQLite 分位数查询失败: " + e.getMessage(), e);
            }

            List<ServiceAggregate> rows = new ArrayList<>();
            for (Map.Entry<String, long[]> entry : stats.entrySet()) {
                long[] row = entry.getValue();
                long count = row[0];
                double avg = count == 0 ? 0.0 : Math.round((row[2] / (double) count) * 100.0) / 100.0;
                List<Long> sorted = durations.getOrDefault(entry.getKey(), List.of());
                rows.add(new ServiceAggregate(
                        entry.getKey(),
                        count,
                        row[1],
                        avg,
                        row[3],
                        ServiceAggregateCalculator.percentile(sorted, 0.50),
                        ServiceAggregateCalculator.percentile(sorted, 0.95)));
            }
            rows.sort(java.util.Comparator.comparing(ServiceAggregate::serviceName));
            return rows;
        }
    }

    @Override
    public int size() {
        synchronized (lock) {
            return sizeUnlocked();
        }
    }

    @Override
    public long evictedCount() {
        return evictedTotal.get();
    }

    private int sizeUnlocked() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM spans")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite count 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int purgeOlderThan(long cutoffEpochMs) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM spans WHERE start_time < ?")) {
                ps.setLong(1, cutoffEpochMs);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    evictedTotal.addAndGet(deleted);
                }
                return deleted;
            } catch (SQLException e) {
                throw new IllegalStateException("SQLite purge 失败: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public int clearAll() {
        synchronized (lock) {
            try (Statement st = connection.createStatement()) {
                int deleted = st.executeUpdate("DELETE FROM spans");
                if (deleted > 0) {
                    evictedTotal.addAndGet(deleted);
                }
                return deleted;
            } catch (SQLException e) {
                throw new IllegalStateException("SQLite clearAll 失败: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public int purgeByService(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return 0;
        }
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM spans WHERE service_name = ?")) {
                ps.setString(1, serviceName);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    evictedTotal.addAndGet(deleted);
                }
                return deleted;
            } catch (SQLException e) {
                throw new IllegalStateException("SQLite purgeByService 失败: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                log.warn("[存储] 关闭 SQLite 连接失败: {}", e.getMessage());
            }
        }
    }

    private TraceSpan readPayload(String json) throws Exception {
        TraceSpan span = objectMapper.readValue(json, TraceSpan.class);
        return TraceSpan.snapshot(span);
    }

    private static long sinceEpochMillis(int lastHours) {
        if (lastHours <= 0) {
            return 0L;
        }
        return Instant.now().minus(lastHours, ChronoUnit.HOURS).toEpochMilli();
    }

    private static long n(Long v) {
        return v != null ? v : 0L;
    }

}
