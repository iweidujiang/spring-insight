package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JDBC {@link DataSource} / {@link Connection} / {@link Statement} 包装与 JDK 动态代理。
 *
 * @since 2026-10-11
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class InsightJdbcProxies {

    private static final Logger log = LoggerFactory.getLogger(InsightJdbcProxies.class);

    /** DataSource 装饰层上限（含已有自定义包装与 Insight 包装） */
    public static final int MAX_DATASOURCE_PROXY_LAYERS = 3;

    private InsightJdbcProxies() {
    }

    /**
     * 包装业务 {@link DataSource}；已是 Insight 包装则原样返回，层数超限则打 WARN 并返回最内层。
     *
     * @param dataSource 原始或已包装的 DataSource
     * @param listener   上报
     * @param properties 配置
     * @return 可安全使用的 DataSource（可能未再包一层）
     */
    public static DataSource wrap(DataSource dataSource,
                                  SpanReportingListener listener,
                                  InsightProperties properties) {
        if (dataSource == null) {
            return null;
        }
        if (dataSource instanceof InsightTracingDataSource) {
            return dataSource;
        }
        int depth = countProxyLayers(dataSource);
        if (depth >= MAX_DATASOURCE_PROXY_LAYERS) {
            DataSource innermost = unwrapInnermost(dataSource);
            log.warn("[JDBC] DataSource 代理层数已达 {}（上限 {}），跳过 Insight 包装，使用最内层 {}",
                    depth, MAX_DATASOURCE_PROXY_LAYERS,
                    innermost == null ? "null" : innermost.getClass().getName());
            return innermost;
        }
        return new InsightTracingDataSource(dataSource, listener, properties);
    }

    /**
     * 统计装饰层数（不含最内层真实 DataSource）；遇环按已达上限处理。
     *
     * @param dataSource 待检测
     * @return 层数
     */
    public static int countProxyLayers(DataSource dataSource) {
        if (dataSource == null) {
            return 0;
        }
        int depth = 0;
        DataSource cur = dataSource;
        Set<DataSource> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (cur != null) {
            if (!seen.add(cur)) {
                return Math.max(depth, MAX_DATASOURCE_PROXY_LAYERS);
            }
            DataSource next = peelOne(cur);
            if (next == null || next == cur) {
                break;
            }
            depth++;
            cur = next;
        }
        return depth;
    }

    /**
     * 沿委托链取最内层 DataSource（遇环则返回环入口前一节点）。
     *
     * @param dataSource 起点
     * @return 最内层；入参为空时为空
     */
    public static DataSource unwrapInnermost(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }
        DataSource cur = dataSource;
        Set<DataSource> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (cur != null) {
            if (!seen.add(cur)) {
                return cur;
            }
            DataSource next = peelOne(cur);
            if (next == null || next == cur) {
                return cur;
            }
            cur = next;
        }
        return dataSource;
    }

    /**
     * 剥开一层包装：Insight {@code getDelegate}、常见 {@code getTargetDataSource}/{@code getDelegate}，
     * 再尝试 unwrap 到内嵌的 {@link InsightTracingDataSource}。
     * <p>
     * 注意：JDBC {@code unwrap(DataSource.class)} 对实现类会返回自身，不能用来剥层。
     * </p>
     *
     * @param ds 当前层
     * @return 内层；无法剥离时为 null
     */
    static DataSource peelOne(DataSource ds) {
        if (ds == null) {
            return null;
        }
        if (ds instanceof InsightTracingDataSource) {
            return ((InsightTracingDataSource) ds).getDelegate();
        }
        DataSource viaAccessor = invokeDataSourceAccessor(ds, "getTargetDataSource");
        if (viaAccessor == null) {
            viaAccessor = invokeDataSourceAccessor(ds, "getDelegate");
        }
        if (viaAccessor != null) {
            return viaAccessor;
        }
        try {
            if (ds.isWrapperFor(InsightTracingDataSource.class)) {
                InsightTracingDataSource insight = ds.unwrap(InsightTracingDataSource.class);
                if (insight != null && insight != ds) {
                    return insight;
                }
            }
        } catch (SQLException ignored) {
            // 忽略无法 unwrap 的实现
        }
        return null;
    }

    private static DataSource invokeDataSourceAccessor(DataSource ds, String methodName) {
        try {
            Method method = ds.getClass().getMethod(methodName);
            if (!DataSource.class.isAssignableFrom(method.getReturnType())) {
                return null;
            }
            Object target = method.invoke(ds);
            if (target instanceof DataSource && target != ds) {
                return (DataSource) target;
            }
        } catch (ReflectiveOperationException ignored) {
            // 非 Spring / 非委托风格包装
        }
        return null;
    }

    /**
     * @param connection 原始连接
     * @param listener   上报
     * @param properties 配置
     * @return 包装后的 Connection
     */
    public static Connection wrapConnection(Connection connection,
                                            SpanReportingListener listener,
                                            InsightProperties properties) {
        if (connection == null) {
            return null;
        }
        if (Proxy.isProxyClass(connection.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(connection);
            if (handler instanceof ConnectionHandler) {
                return connection;
            }
            // 其它 JDK 代理不再套一层，避免多层代理递归
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
        private final InsightProperties properties;
        private final String remote;

        ConnectionHandler(Connection delegate,
                          SpanReportingListener listener,
                          InsightProperties properties,
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
        private final InsightProperties properties;
        private final String remote;
        private final AtomicReference<String> sqlRef;

        StatementHandler(Statement delegate,
                         SpanReportingListener listener,
                         InsightProperties properties,
                         String remote,
                         String preparedSql) {
            this.delegate = delegate;
            this.listener = listener;
            this.properties = properties;
            this.remote = remote;
            this.sqlRef = new AtomicReference<>(preparedSql);
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
                            () -> {
                                try {
                                    return method.invoke(delegate, args);
                                } catch (InvocationTargetException e) {
                                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                                    if (cause instanceof Exception) {
                                        throw (Exception) cause;
                                    }
                                    throw new SQLException(cause);
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
        for (Object arg : args) {
            if (arg instanceof String) {
                return (String) arg;
            }
        }
        return null;
    }
}
