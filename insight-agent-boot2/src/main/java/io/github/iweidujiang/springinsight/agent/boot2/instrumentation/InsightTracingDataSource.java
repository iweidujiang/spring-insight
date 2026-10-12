package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * 包装业务 {@link DataSource}：连接上的 Statement 执行会按配置上报 JDBC CLIENT Span。
 *
 * @since 2026-10-11
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class InsightTracingDataSource implements DataSource {

    private final DataSource delegate;
    private final SpanReportingListener listener;
    private final InsightBoot2Properties properties;

    /**
     * @param delegate   原始 DataSource
     * @param listener   上报
     * @param properties 配置
     */
    public InsightTracingDataSource(DataSource delegate,
                                    SpanReportingListener listener,
                                    InsightBoot2Properties properties) {
        this.delegate = delegate;
        this.listener = listener;
        this.properties = properties;
    }

    /**
     * @return 被包装的原始 DataSource
     */
    public DataSource getDelegate() {
        return delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return InsightJdbcProxies.wrapConnection(delegate.getConnection(), listener, properties);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return InsightJdbcProxies.wrapConnection(
                delegate.getConnection(username, password), listener, properties);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
}
