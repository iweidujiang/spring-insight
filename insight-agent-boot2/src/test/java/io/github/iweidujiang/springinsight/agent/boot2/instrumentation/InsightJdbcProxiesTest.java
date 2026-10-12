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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DataSource 连接代理：PreparedStatement.execute 上报 CLIENT Span。
 *
 * @since 2026-10-11
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
public class InsightJdbcProxiesTest {

    @Mock
    private SpanReportingListener listener;

    private InsightBoot2Properties properties;

    /**
     * 阈值 0，成功也上报。
     */
    @BeforeEach
    public void setUp() {
        properties = new InsightBoot2Properties();
        properties.setEnabled(true);
        properties.setJdbcTracingEnabled(true);
        properties.setJdbcSlowThresholdMs(0L);
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
     * PreparedStatement.execute 产生 JDBC CLIENT Span。
     *
     * @throws Exception JDBC mock
     */
    @Test
    public void prepareStatementExecute_reportsClientSpan() throws Exception {
        Connection raw = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(raw.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:mysql://orders-db:3306/app");

        PreparedStatement ps = mock(PreparedStatement.class);
        when(raw.prepareStatement("SELECT 1")).thenReturn(ps);
        when(ps.execute()).thenReturn(true);

        TraceContext.startSpan("HTTP GET /orders");
        Connection wrapped = InsightJdbcProxies.wrapConnection(raw, listener, properties);
        PreparedStatement wrappedPs = wrapped.prepareStatement("SELECT 1");
        assertTrue(wrappedPs.execute());

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        TraceSpan span = captor.getValue();
        assertEquals("CLIENT", span.getSpanKind());
        assertEquals("JDBC", span.getComponent());
        assertEquals("DB SELECT", span.getOperationName());
        assertEquals("orders-db:3306", span.getRemoteService());
        assertEquals(1, TraceContext.getStackDepth());
        verify(ps).execute();
    }
}
