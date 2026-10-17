package io.github.iweidujiang.springinsight.storage.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.spi.ServiceAggregate;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import io.github.iweidujiang.springinsight.storage.spi.TraceSummary;
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
    public List<TraceSummary> listTraces(long sinceEpoch, String service, String status, String query,
                                          long minDurationMs, String pathPrefix, int offset, int limit, String sort) {
        if (needsPayloadFilter(query, pathPrefix)) {
            return TraceSummaryCalculator.page(
                    TraceSummaryCalculator.collect(snapshot(), sinceEpoch, service, status, query, minDurationMs, pathPrefix),
                    offset, limit, sort);
        }
        synchronized (lock) {
            List<TraceSummary> page = queryTracePage(sinceEpoch, service, status, minDurationMs, offset, limit, sort);
            return enrichRoots(page);
        }
    }

    @Override
    public long countTraces(long sinceEpoch, String service, String status, String query,
                            long minDurationMs, String pathPrefix) {
        if (needsPayloadFilter(query, pathPrefix)) {
            return TraceSummaryCalculator.collect(
                    snapshot(), sinceEpoch, service, status, query, minDurationMs, pathPrefix).size();
        }
        synchronized (lock) {
            String filter = service == null ? "" : service.trim();
            String sql = "SELECT COUNT(*) FROM (" + groupedTraceSql(filter, status) + ") grouped";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bindTraceFilter(ps, sinceEpoch, minDurationMs, filter);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("SQLite 链路计数失败: " + e.getMessage(), e);
            }
        }
    }

    private List<TraceSummary> queryTracePage(long sinceEpoch, String service, String status,
                                               long minDurationMs, int offset, int limit, String sort) {
        String filter = service == null ? "" : service.trim();
        int off = Math.max(0, offset);
        int lim = Math.min(200, Math.max(1, limit));
        String sql = "SELECT trace_id, start_time, duration_ms, span_count, service_count, has_error FROM ("
                + groupedTraceSql(filter, status) + ") grouped ORDER BY " + orderBy(sort) + " LIMIT ? OFFSET ?";
        List<TraceSummary> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int next = bindTraceFilter(ps, sinceEpoch, minDurationMs, filter);
            ps.setInt(next, lim);
            ps.setInt(next + 1, off);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean error = rs.getInt(6) != 0;
                    rows.add(new TraceSummary(
                            rs.getString(1),
                            "",
                            "(unknown)",
                            rs.getLong(2),
                            Math.max(0L, rs.getLong(3)),
                            rs.getInt(4),
                            rs.getInt(5),
                            error,
                            error ? "ERROR" : "OK"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite 链路分页失败: " + e.getMessage(), e);
        }
        return rows;
    }

    /**
     * 分组子查询。外层可再套排序、分页或计数。
     */
    private static String groupedTraceSql(String service, String status) {
        String serviceClause = service == null || service.isEmpty()
                ? ""
                : " AND SUM(CASE WHEN service_name = ? OR json_extract(payload, '$.remoteService') = ? THEN 1 ELSE 0 END) > 0 ";
        String statusClause = switch (status == null ? "all" : status.trim().toLowerCase()) {
            case "error" -> " AND MAX(error) != 0 ";
            case "ok" -> " AND MAX(error) = 0 ";
            default -> "";
        };
        return """
                SELECT trace_id,
                       MIN(start_time) AS start_time,
                       MAX(start_time + duration_ms) - MIN(start_time) AS duration_ms,
                       COUNT(*) AS span_count,
                       COUNT(DISTINCT CASE WHEN service_name IS NOT NULL AND service_name != '' THEN service_name END) AS service_count,
                       MAX(error) AS has_error
                FROM spans
                WHERE start_time >= ?
                GROUP BY trace_id
                HAVING (MAX(start_time + duration_ms) - MIN(start_time)) >= ?
                """ + serviceClause + statusClause;
    }

    private static int bindTraceFilter(PreparedStatement ps, long sinceEpoch, long minDurationMs, String service)
            throws SQLException {
        ps.setLong(1, Math.max(0L, sinceEpoch));
        ps.setLong(2, Math.max(0L, minDurationMs));
        if (service != null && !service.isEmpty()) {
            ps.setString(3, service);
            ps.setString(4, service);
            return 5;
        }
        return 3;
    }

    private static String orderBy(String sort) {
        return switch (TraceSummaryCalculator.normalizeSort(sort)) {
            case "time_asc" -> "start_time ASC, trace_id ASC";
            case "duration_desc" -> "duration_ms DESC, trace_id ASC";
            case "duration_asc" -> "duration_ms ASC, trace_id ASC";
            default -> "start_time DESC, trace_id ASC";
        };
    }

    private List<TraceSummary> enrichRoots(List<TraceSummary> page) {
        if (page.isEmpty()) {
            return page;
        }
        Map<String, List<TraceSpan>> spansByTrace = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("SELECT payload FROM spans WHERE trace_id IN (");
        for (int i = 0; i < page.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(')');
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < page.size(); i++) {
                ps.setString(i + 1, page.get(i).traceId());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TraceSpan span = readPayload(rs.getString(1));
                    spansByTrace.computeIfAbsent(span.getTraceId(), key -> new ArrayList<>()).add(span);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("SQLite 读取链路根 Span 失败: " + e.getMessage(), e);
        }
        List<TraceSummary> enriched = new ArrayList<>(page.size());
        for (TraceSummary row : page) {
            TraceSpan root = TraceSummaryCalculator.pickRoot(spansByTrace.get(row.traceId()));
            String operation = root != null && root.getOperationName() != null && !root.getOperationName().isBlank()
                    ? root.getOperationName() : "(unknown)";
            String serviceName = root != null && root.getServiceName() != null ? root.getServiceName() : "";
            enriched.add(new TraceSummary(
                    row.traceId(),
                    serviceName,
                    operation,
                    row.startTime(),
                    row.durationMs(),
                    row.spanCount(),
                    row.serviceCount(),
                    row.hasError(),
                    row.statusCode()));
        }
        return enriched;
    }

    private static boolean needsPayloadFilter(String query, String pathPrefix) {
        return (query != null && !query.isBlank()) || (pathPrefix != null && !pathPrefix.isBlank());
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
