package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JDBC {@link Connection} / {@link Statement} JDK 动态代理。
 *
 * @since 2026-10-11
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class InsightJdbcProxies {

    private InsightJdbcProxies() {
    }

    /**
     * @param connection 原始连接
     * @param listener   上报
     * @param properties 配置
     * @return 包装后的 Connection
     */
    public static Connection wrapConnection(Connection connection,
                                            SpanReportingListener listener,
                                            InsightBoot2Properties properties) {
        if (connection == null || Proxy.isProxyClass(connection.getClass())) {
            return connection;
        }
        String remote = resolveRemote(connection);
        ClassLoader cl = connection.getClass().getClassLoader();
        return (Connection) Proxy.newProxyInstance(
                cl,
                new Class<?>[]{Connection.class},
                new ConnectionHandler(connection, listener, properties, remote));
    }

    private static String resolveRemote(Connection connection) {
        try {
            return InsightJdbcSupport.resolveRemoteFromUrl(connection.getMetaData().getURL());
        } catch (Exception e) {
            return "";
        }
    }

    private static final class ConnectionHandler implements InvocationHandler {
        private final Connection delegate;
        private final SpanReportingListener listener;
        private final InsightBoot2Properties properties;
        private final String remote;

        ConnectionHandler(Connection delegate,
                          SpanReportingListener listener,
                          InsightBoot2Properties properties,
                          String remote) {
            this.delegate = delegate;
            this.listener = listener;
            this.properties = properties;
            this.remote = remote;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            try {
                if ("unwrap".equals(name) && args != null && args.length == 1 && args[0] instanceof Class) {
                    Class<?> iface = (Class<?>) args[0];
                    if (iface.isInstance(proxy)) {
                        return proxy;
                    }
                    return delegate.unwrap(iface);
                }
                if ("isWrapperFor".equals(name) && args != null && args.length == 1 && args[0] instanceof Class) {
                    Class<?> iface = (Class<?>) args[0];
                    return iface.isInstance(proxy) || delegate.isWrapperFor(iface);
                }
                Object result = method.invoke(delegate, args);
                if (result instanceof CallableStatement) {
                    String sql = firstStringArg(args);
                    return wrapStatement((CallableStatement) result, CallableStatement.class, sql);
                }
                if (result instanceof PreparedStatement) {
                    String sql = firstStringArg(args);
                    return wrapStatement((PreparedStatement) result, PreparedStatement.class, sql);
                }
                if (result instanceof Statement) {
                    return wrapStatement((Statement) result, Statement.class, null);
                }
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }

        private Object wrapStatement(Statement statement, Class<?> iface, String preparedSql) {
            ClassLoader cl = statement.getClass().getClassLoader();
            return Proxy.newProxyInstance(
                    cl,
                    new Class<?>[]{iface},
                    new StatementHandler(statement, listener, properties, remote, preparedSql));
        }
    }

    private static final class StatementHandler implements InvocationHandler {
        private final Statement delegate;
        private final SpanReportingListener listener;
        private final InsightBoot2Properties properties;
        private final String remote;
        private final AtomicReference<String> sqlRef;

        StatementHandler(Statement delegate,
                         SpanReportingListener listener,
                         InsightBoot2Properties properties,
                         String remote,
                         String preparedSql) {
            this.delegate = delegate;
            this.listener = listener;
            this.properties = properties;
            this.remote = remote;
            this.sqlRef = new AtomicReference<String>(preparedSql);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            try {
                if ("unwrap".equals(name) && args != null && args.length == 1 && args[0] instanceof Class) {
                    Class<?> iface = (Class<?>) args[0];
                    if (iface.isInstance(proxy)) {
                        return proxy;
                    }
                    return delegate.unwrap(iface);
                }
                if ("isWrapperFor".equals(name) && args != null && args.length == 1 && args[0] instanceof Class) {
                    Class<?> iface = (Class<?>) args[0];
                    return iface.isInstance(proxy) || delegate.isWrapperFor(iface);
                }
                if (name.startsWith("execute")) {
                    String sql = resolveSql(args);
                    return InsightJdbcSupport.traceExecute(
                            sql, remote, properties, listener,
                            new InsightJdbcSupport.SqlCallable<Object>() {
                                @Override
                                public Object call() throws Exception {
                                    try {
                                        return method.invoke(delegate, args);
                                    } catch (InvocationTargetException e) {
                                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                                        if (cause instanceof Exception) {
                                            throw (Exception) cause;
                                        }
                                        throw new SQLException(cause);
                                    }
                                }
                            });
                }
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }

        private String resolveSql(Object[] args) {
            String fromArg = firstStringArg(args);
            if (StringUtils.hasText(fromArg)) {
                return fromArg;
            }
            return sqlRef.get();
        }
    }

    private static String firstStringArg(Object[] args) {
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String) {
                return (String) args[i];
            }
        }
        return null;
    }
}
