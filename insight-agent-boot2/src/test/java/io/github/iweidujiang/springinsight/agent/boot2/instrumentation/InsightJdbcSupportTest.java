package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
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
public class InsightJdbcSupportTest {

    @Mock
    private SpanReportingListener listener;

    private InsightBoot2Properties properties;

    /**
     * 默认开启 JDBC 追踪，阈值 500ms。
     */
    @BeforeEach
    public void setUp() {
        properties = new InsightBoot2Properties();
        properties.setEnabled(true);
        properties.setJdbcTracingEnabled(true);
        properties.setJdbcSlowThresholdMs(500L);
        TraceContext.clear();
    }

    /**
     * 清 Trace 栈。
     */
    @AfterEach
    public void tearDown() {
        TraceContext.clear();
    }

    /**
     * SQL 类型与截断。
     */
    @Test
    public void sqlTypeAndTruncate() {
        assertEquals("SELECT", InsightJdbcSupport.sqlType("select * from t"));
        assertEquals("INSERT", InsightJdbcSupport.sqlType("INSERT INTO t VALUES (1)"));
        assertEquals("UNKNOWN", InsightJdbcSupport.sqlType("/* comment */"));
        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < 600; i++) {
            sb.append('x');
        }
        String truncated = InsightJdbcSupport.truncateSql(sb.toString());
        assertTrue(truncated.endsWith("…"));
        assertTrue(truncated.length() <= 513);
    }

    /**
     * 错误必报；成功看阈值。
     */
    @Test
    public void shouldReport_errorAlways_slowByThreshold() {
        assertTrue(InsightJdbcSupport.shouldReport(properties, 1L, true));
        assertFalse(InsightJdbcSupport.shouldReport(properties, 100L, false));
        assertTrue(InsightJdbcSupport.shouldReport(properties, 500L, false));
        properties.setJdbcSlowThresholdMs(0L);
        assertTrue(InsightJdbcSupport.shouldReport(properties, 1L, false));
    }

    /**
     * 无父 Span 不上报。
     *
     * @throws Exception 不应抛出
     */
    @Test
    public void noParent_skipsReport() throws Exception {
        Object out = InsightJdbcSupport.traceExecute(
                "SELECT 1", "localhost:3306", properties, listener,
                new InsightJdbcSupport.SqlCallable<Object>() {
                    @Override
                    public Object call() {
                        return "ok";
                    }
                });
        assertEquals("ok", out);
        verify(listener, never()).reportSpan(any(TraceSpan.class));
        assertEquals(0, TraceContext.getStackDepth());
    }

    /**
     * 快成功不上报、不压栈。
     *
     * @throws Exception 不应抛出
     */
    @Test
    public void fastSuccess_skipsReport() throws Exception {
        TraceContext.startSpan("HTTP GET /x");
        InsightJdbcSupport.traceExecute(
                "SELECT 1", "db", properties, listener,
                new InsightJdbcSupport.SqlCallable<Integer>() {
                    @Override
                    public Integer call() {
                        return 1;
                    }
                });
        verify(listener, never()).reportSpan(any(TraceSpan.class));
        assertEquals(1, TraceContext.getStackDepth());
    }

    /**
     * SQLException 上报且不压栈。
     */
    @Test
    public void error_reportsSqlErrorWithoutPushingStack() {
        TraceContext.startSpan("HTTP GET /x");
        assertThrows(SQLException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                InsightJdbcSupport.traceExecute(
                        "UPDATE t SET a=1", "db", properties, listener,
                        new InsightJdbcSupport.SqlCallable<Void>() {
                            @Override
                            public Void call() throws SQLException {
                                throw new SQLException("boom");
                            }
                        });
            }
        });
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

    /**
     * 阈值为 0 时成功也上报。
     *
     * @throws Exception 不应抛出
     */
    @Test
    public void thresholdZero_reportsSuccess() throws Exception {
        properties.setJdbcSlowThresholdMs(0L);
        TraceContext.startSpan("HTTP GET /x");
        InsightJdbcSupport.traceExecute(
                "SELECT id FROM orders", "orders-db", properties, listener,
                new InsightJdbcSupport.SqlCallable<Boolean>() {
                    @Override
                    public Boolean call() {
                        return Boolean.TRUE;
                    }
                });
        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        assertEquals("DB SELECT", captor.getValue().getOperationName());
        assertEquals("orders-db", captor.getValue().getRemoteService());
        assertEquals("SELECT", captor.getValue().getTags().get("db.sql_type"));
    }

    /**
     * JDBC URL → 短逻辑 remoteService。
     */
    @Test
    public void resolveRemoteFromUrl_shortLogicalNames() {
        assertEquals("h2:mem:sca_user_demo", InsightJdbcSupport.resolveRemoteFromUrl(
                "jdbc:h2:mem:sca_user_demo;MODE=MySQL;DB_CLOSE_DELAY=-1"));
        assertEquals("orders-db:3306", InsightJdbcSupport.resolveRemoteFromUrl(
                "jdbc:mysql://orders-db:3306/app"));
        assertEquals("mysql:app", InsightJdbcSupport.resolveRemoteFromUrl(
                "jdbc:mysql://127.0.0.1:3306/app"));
    }
}
