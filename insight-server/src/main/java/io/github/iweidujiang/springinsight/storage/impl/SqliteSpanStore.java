package io.github.iweidujiang.springinsight.storage.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
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
import java.util.List;
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
                      payload TEXT NOT NULL
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_spans_trace ON spans(trace_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_spans_start ON spans(start_time)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_spans_service ON spans(service_name)");
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
                String sql = "INSERT INTO spans(trace_id, span_id, service_name, start_time, payload) VALUES (?,?,?,?,?)";
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
