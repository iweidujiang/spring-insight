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

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
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

    @BeforeEach
    void setUp() {
        properties = new InsightBoot2Properties();
        properties.setEnabled(true);
        properties.setJdbcTracingEnabled(true);
        properties.setJdbcSlowThresholdMs(0L);
        TraceContext.clear();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void prepareStatementExecute_reportsClientSpan() throws Exception {
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

    @Test
    void h2MemUrl_usesShortRemoteService() throws Exception {
        Connection raw = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(raw.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:h2:mem:boot2_provider_demo;MODE=MySQL;DB_CLOSE_DELAY=-1");

        PreparedStatement ps = mock(PreparedStatement.class);
        when(raw.prepareStatement("SELECT 1")).thenReturn(ps);
        when(ps.execute()).thenReturn(true);

        TraceContext.startSpan("HTTP GET /api/jdbc/slow");
        Connection wrapped = InsightJdbcProxies.wrapConnection(raw, listener, properties);
        assertTrue(wrapped.prepareStatement("SELECT 1").execute());

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        assertEquals("h2:mem:boot2_provider_demo", captor.getValue().getRemoteService());
    }

    @Test
    void wrapSameDataSourceTwice_returnsSameInstance() throws Exception {
        DataSource raw = stubDataSource(mockConnection());
        DataSource first = InsightJdbcProxies.wrap(raw, listener, properties);
        DataSource second = InsightJdbcProxies.wrap(first, listener, properties);
        assertTrue(first instanceof InsightTracingDataSource);
        assertSame(first, second);
    }

    @Test
    void doubleProxy_getConnectionDoesNotOverflow() throws Exception {
        Connection rawConn = mockConnection();
        PreparedStatement ps = mock(PreparedStatement.class);
        when(rawConn.prepareStatement("SELECT 1")).thenReturn(ps);
        when(ps.execute()).thenReturn(true);

        DataSource hikari = stubDataSource(rawConn);
        DataSource custom = new DelegatingDataSource(hikari);
        DataSource insight = InsightJdbcProxies.wrap(custom, listener, properties);

        assertEquals(2, InsightJdbcProxies.countProxyLayers(insight));
        TraceContext.startSpan("HTTP GET /orders");
        try (Connection c = insight.getConnection()) {
            assertTrue(c.prepareStatement("SELECT 1").execute());
        }
        verify(listener).reportSpan(org.mockito.ArgumentMatchers.any(TraceSpan.class));
    }

    @Test
    void threeProxyLayers_stillWorks() throws Exception {
        Connection rawConn = mockConnection();
        PreparedStatement ps = mock(PreparedStatement.class);
        when(rawConn.prepareStatement("SELECT 1")).thenReturn(ps);
        when(ps.execute()).thenReturn(true);

        DataSource hikari = stubDataSource(rawConn);
        DataSource layer1 = new DelegatingDataSource(hikari);
        DataSource layer2 = new DelegatingDataSource(layer1);
        DataSource insight = InsightJdbcProxies.wrap(layer2, listener, properties);

        assertTrue(insight instanceof InsightTracingDataSource);
        assertEquals(3, InsightJdbcProxies.countProxyLayers(insight));

        TraceContext.startSpan("HTTP GET /orders");
        try (Connection c = insight.getConnection()) {
            assertTrue(c.prepareStatement("SELECT 1").execute());
        }
    }

    @Test
    void overMaxLayers_returnsInnermostWithoutWrapping() throws Exception {
        Connection rawConn = mockConnection();
        DataSource hikari = stubDataSource(rawConn);
        DataSource layer1 = new DelegatingDataSource(hikari);
        DataSource layer2 = new DelegatingDataSource(layer1);
        DataSource layer3 = new DelegatingDataSource(layer2);

        assertEquals(3, InsightJdbcProxies.countProxyLayers(layer3));
        DataSource result = InsightJdbcProxies.wrap(layer3, listener, properties);
        assertFalse(result instanceof InsightTracingDataSource);
        assertSame(hikari, result);
    }

    private Connection mockConnection() throws Exception {
        Connection raw = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        lenient().when(raw.getMetaData()).thenReturn(meta);
        lenient().when(meta.getURL()).thenReturn("jdbc:mysql://orders-db:3306/app");
        return raw;
    }

    private static DataSource stubDataSource(Connection connection) {
        return new DataSource() {
            @Override
            public Connection getConnection() {
                return connection;
            }

            @Override
            public Connection getConnection(String username, String password) {
                return connection;
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                if (iface.isInstance(this)) {
                    return iface.cast(this);
                }
                throw new SQLException("not a wrapper");
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return iface.isInstance(this);
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public Logger getParentLogger() throws SQLFeatureNotSupportedException {
                throw new SQLFeatureNotSupportedException();
            }
        };
    }

    /**
     * 模拟用户自定义 DataSource 装饰器（提供 getTargetDataSource，与 Spring 风格一致）。
     */
    private static final class DelegatingDataSource implements DataSource {
        private final DataSource target;

        private DelegatingDataSource(DataSource target) {
            this.target = target;
        }

        /** 供 {@link InsightJdbcProxies#peelOne} 反射剥层 */
        public DataSource getTargetDataSource() {
            return target;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return target.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return target.getConnection(username, password);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            return target.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || target.isWrapperFor(iface);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return target.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            target.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            target.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return target.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return target.getParentLogger();
        }
    }
}
