package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * JDBC 追踪：SQL 解析、慢阈值与错误上报。
 *
 * @since 2026-10-11
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
class InsightJdbcSupportTest {

    @Mock
    private SpanReportingListener listener;

    private InsightProperties properties;

    @BeforeEach
    void setUp() {
        properties = new InsightProperties();
        properties.setEnabled(true);
        properties.setJdbcTracingEnabled(true);
        properties.setJdbcSlowThresholdMs(500L);
        TraceContext.clear();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void sqlTypeAndTruncate() {
        assertEquals("SELECT", InsightJdbcSupport.sqlType("select * from t"));
        assertEquals("INSERT", InsightJdbcSupport.sqlType("INSERT INTO t VALUES (1)"));
        assertEquals("UNKNOWN", InsightJdbcSupport.sqlType("/* comment */"));
        String longSql = "SELECT " + "x".repeat(600);
        String truncated = InsightJdbcSupport.truncateSql(longSql);
        assertTrue(truncated.endsWith("…"));
        assertTrue(truncated.length() <= 513);
    }

    @Test
    void shouldReport_errorAlways_slowByThreshold() {
        assertTrue(InsightJdbcSupport.shouldReport(properties, 1L, true));
        assertFalse(InsightJdbcSupport.shouldReport(properties, 100L, false));
        assertTrue(InsightJdbcSupport.shouldReport(properties, 500L, false));
        properties.setJdbcSlowThresholdMs(0L);
        assertTrue(InsightJdbcSupport.shouldReport(properties, 1L, false));
    }

    @Test
    void noParent_skipsReport() throws Exception {
        Object out = InsightJdbcSupport.traceExecute(
                "SELECT 1", "localhost:3306", properties, listener, () -> "ok");
        assertEquals("ok", out);
        verify(listener, never()).reportSpan(any());
        assertEquals(0, TraceContext.getStackDepth());
    }

    @Test
    void fastSuccess_skipsReport() throws Exception {
        TraceContext.startSpan("HTTP GET /x");
        InsightJdbcSupport.traceExecute(
                "SELECT 1", "db", properties, listener, () -> 1);
        verify(listener, never()).reportSpan(any());
        assertEquals(1, TraceContext.getStackDepth());
    }

    @Test
    void error_reportsSqlErrorWithoutPushingStack() {
        TraceContext.startSpan("HTTP GET /x");
        assertThrows(SQLException.class, () ->
                InsightJdbcSupport.traceExecute(
                        "UPDATE t SET a=1", "db", properties, listener, () -> {
                            throw new SQLException("boom");
                        }));
        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        TraceSpan span = captor.getValue();
        assertEquals("CLIENT", span.getSpanKind());
        assertEquals("JDBC", span.getComponent());
        assertEquals("DB UPDATE", span.getOperationName());
        assertEquals("ERROR", span.getStatusCode());
        assertEquals("SQL_ERROR", span.getErrorCode());
        assertTrue(span.getErrorMessage() != null && span.getErrorMessage().contains("boom"));
        assertEquals(1, TraceContext.getStackDepth());
    }

    @Test
    void thresholdZero_reportsSuccess() throws Exception {
        properties.setJdbcSlowThresholdMs(0L);
        TraceContext.startSpan("HTTP GET /x");
        InsightJdbcSupport.traceExecute(
                "SELECT id FROM orders", "orders-db", properties, listener, () -> true);
        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        assertEquals("DB SELECT", captor.getValue().getOperationName());
        assertEquals("orders-db", captor.getValue().getRemoteService());
        assertEquals("SELECT", captor.getValue().getTags().get("db.sql_type"));
    }

    @Test
    void resolveRemoteFromUrl_shortLogicalNames() {
        assertEquals("h2:mem:sca_user_demo", InsightJdbcSupport.resolveRemoteFromUrl(
                "jdbc:h2:mem:sca_user_demo;MODE=MySQL;DB_CLOSE_DELAY=-1"));
        assertEquals("orders-db:3306", InsightJdbcSupport.resolveRemoteFromUrl(
                "jdbc:mysql://orders-db:3306/app"));
        assertEquals("mysql:app", InsightJdbcSupport.resolveRemoteFromUrl(
                "jdbc:mysql://127.0.0.1:3306/app"));
        assertEquals("mysql:app", InsightJdbcSupport.resolveRemoteFromUrl(
                "jdbc:mysql://localhost:3306/app"));
        assertEquals("sqlite:demo.db", InsightJdbcSupport.resolveRemoteFromUrl(
                "jdbc:sqlite:demo.db"));
    }
}
