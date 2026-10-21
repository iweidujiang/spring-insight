package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 包装业务 {@link DataSource}：连接上的 Statement 执行会按配置上报 JDBC CLIENT Span。
 * <p>
 * 请优先通过 {@link InsightJdbcProxies#wrap} 创建，以自动防重入并限制代理层数。
 * </p>
 *
 * @since 2026-10-11
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class InsightTracingDataSource implements DataSource {

    private final DataSource delegate;
    private final SpanReportingListener listener;
    private final InsightProperties properties;

    /**
     * @param delegate   原始 DataSource
     * @param listener   上报
     * @param properties 配置
     */
    public InsightTracingDataSource(DataSource delegate,
                                    SpanReportingListener listener,
                                    InsightProperties properties) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate DataSource 不能为空");
        }
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
        return unwrapWithCycleGuard(delegate, iface, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || isWrapperForWithCycleGuard(
                delegate, iface, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static <T> T unwrapWithCycleGuard(DataSource cur, Class<T> iface, Set<DataSource> seen)
            throws SQLException {
        if (cur == null) {
            throw new SQLException("无法 unwrap 到 " + iface.getName());
        }
        if (!seen.add(cur)) {
            throw new SQLException("DataSource 委托链存在环，无法 unwrap " + iface.getName());
        }
        if (iface.isInstance(cur)) {
            return iface.cast(cur);
        }
        if (cur instanceof InsightTracingDataSource) {
            return unwrapWithCycleGuard(((InsightTracingDataSource) cur).getDelegate(), iface, seen);
        }
        return cur.unwrap(iface);
    }

    private static boolean isWrapperForWithCycleGuard(DataSource cur, Class<?> iface, Set<DataSource> seen)
            throws SQLException {
        if (cur == null) {
            return false;
        }
        if (!seen.add(cur)) {
            return false;
        }
        if (iface.isInstance(cur)) {
            return true;
        }
        if (cur instanceof InsightTracingDataSource) {
            return isWrapperForWithCycleGuard(((InsightTracingDataSource) cur).getDelegate(), iface, seen);
        }
        return cur.isWrapperFor(iface);
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
