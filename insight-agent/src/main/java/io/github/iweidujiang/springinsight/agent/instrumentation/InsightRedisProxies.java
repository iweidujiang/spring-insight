package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis {@link RedisConnectionFactory} / {@link RedisConnection} / JedisPool JDK·CGLIB 代理。
 * <p>工厂代理保留原 Bean 全部接口（如 Lettuce + Reactive），仅包装同步连接与 Lettuce 原生异步命令；
 * Reactive 连接原样透传。JedisPool{@code getResource} 返回的连接会被包装，{@code close} 归还后清理代理状态。</p>
 *
 * @since 2026-10-12
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class InsightRedisProxies {

    private static final Set<String> SKIP_METHODS = new HashSet<>(Arrays.asList(
            "close", "isClosed", "getNativeConnection", "getSentinelConnection",
            "isQueueing", "isPipelined", "openPipeline", "closePipeline",
            "multi", "exec", "discard", "watch", "unwatch",
            "select", "echo", "ping",
            "equals", "hashCode", "toString",
            "setConvertPipelineAndTxResults", "getConvertPipelineAndTxResults",
            "keyCommands", "stringCommands", "listCommands", "setCommands",
            "zSetCommands", "hashCommands", "geoCommands", "scriptingCommands",
            "serverCommands", "streamCommands", "hyperLogLogCommands",
            "clusterGetNodes", "clusterGetSlaves", "clusterGetMasterSlaveMap"
    ));

    /** Jedis 连接上不追踪的方法（含生命周期与元数据）。 */
    private static final Set<String> JEDIS_SKIP_METHODS = new HashSet<>(Arrays.asList(
            "close", "connect", "disconnect", "quit", "resetState",
            "equals", "hashCode", "toString", "clone",
            "getClient", "getConnection", "isConnected", "isBroken",
            "getDB", "getClientName", "ping", "echo", "select",
            "auth", "readonly", "readwrite", "replicaOf", "replicaOfNoOne"
    ));

    /** Lettuce StatefulConnection 上不追踪、仅拦截以包装命令视图的方法。 */
    private static final Set<String> NATIVE_VIEW_METHODS = new HashSet<>(Arrays.asList(
            "async", "sync", "reactive"
    ));

    private static final Set<String> REACTIVE_FACTORY_METHODS = new HashSet<>(Arrays.asList(
            "getReactiveConnection", "getReactiveClusterConnection"
    ));

    private InsightRedisProxies() {
    }

    /**
     * 是否已由本类包装。
     *
     * @param bean 候选 Bean
     * @return true 表示已是本代理
     */
    public static boolean isInsightWrapped(Object bean) {
        if (bean == null) {
            return false;
        }
        if (Proxy.isProxyClass(bean.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(bean);
            return handler instanceof FactoryHandler
                    || handler instanceof ConnectionHandler
                    || handler instanceof NativeConnectionHandler
                    || handler instanceof CommandsHandler;
        }
        if (bean instanceof Advised advised) {
            for (Advisor advisor : advised.getAdvisors()) {
                Object advice = advisor.getAdvice();
                if (advice instanceof JedisPoolGetResourceInterceptor
                        || advice instanceof JedisCommandInterceptor) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 是否为 Jedis 连接池类型（避免编译期依赖 Jedis）。
     *
     * @param bean 候选 Bean
     * @return true 表示为 JedisPool / JedisSentinelPool
     * @author 苏渡苇
     * @since 2026-10-21
     */
    public static boolean isJedisPool(Object bean) {
        if (bean == null) {
            return false;
        }
        for (Class<?> c = bean.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            String name = c.getName();
            if ("redis.clients.jedis.JedisPool".equals(name)
                    || "redis.clients.jedis.JedisSentinelPool".equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param factory    原始工厂
     * @param listener   上报
     * @param properties 配置
     * @return 保留全部接口的包装工厂；非 {@link RedisConnectionFactory} 原样返回
     */
    public static Object wrapFactory(Object factory,
                                     SpanReportingListener listener,
                                     InsightProperties properties) {
        if (factory == null || !(factory instanceof RedisConnectionFactory) || isInsightWrapped(factory)) {
            return factory;
        }
        String remote = InsightRedisSupport.resolveRemote(factory);
        Class<?>[] ifaces = ClassUtils.getAllInterfaces(factory);
        if (ifaces.length == 0) {
            ifaces = new Class<?>[]{RedisConnectionFactory.class};
        }
        ClassLoader cl = factory.getClass().getClassLoader();
        return Proxy.newProxyInstance(
                cl,
                ifaces,
                new FactoryHandler(factory, listener, properties, remote));
    }

    /**
     * 包装 Jedis 连接池：拦截 {@code getResource()}，归还（{@code close}）后清理代理状态。
     *
     * @param pool       JedisPool / JedisSentinelPool
     * @param listener   上报
     * @param properties 配置
     * @return CGLIB 代理池；非 Jedis 池原样返回
     * @author 苏渡苇
     * @since 2026-10-21
     */
    public static Object wrapJedisPool(Object pool,
                                       SpanReportingListener listener,
                                       InsightProperties properties) {
        if (pool == null || !isJedisPool(pool) || isInsightWrapped(pool)) {
            return pool;
        }
        String remote = InsightRedisSupport.resolveRemote(pool);
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(pool);
        proxyFactory.setProxyTargetClass(true);
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("getResource");
        proxyFactory.addAdvisor(new DefaultPointcutAdvisor(
                pointcut, new JedisPoolGetResourceInterceptor(listener, properties, remote)));
        ClassLoader cl = pool.getClass().getClassLoader();
        return proxyFactory.getProxy(cl != null ? cl : ClassUtils.getDefaultClassLoader());
    }

    /**
     * 包装 Jedis 客户端命令（CGLIB 子类，保证仍可转型为 {@code Jedis}）；
     * {@code close} 归还连接池后标记已关闭并清理代理状态。
     *
     * @param jedis      原始 Jedis
     * @param listener   上报
     * @param properties 配置
     * @param remote     remoteService
     * @return 包装后的 Jedis
     * @author 苏渡苇
     * @since 2026-10-21
     */
    public static Object wrapJedis(Object jedis,
                                   SpanReportingListener listener,
                                   InsightProperties properties,
                                   String remote) {
        if (jedis == null || isInsightWrapped(jedis)) {
            return jedis;
        }
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(jedis);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new JedisCommandInterceptor(listener, properties, remote));
        ClassLoader cl = jedis.getClass().getClassLoader();
        return proxyFactory.getProxy(cl != null ? cl : ClassUtils.getDefaultClassLoader());
    }

    /**
     * @param connection 原始连接
     * @param listener   上报
     * @param properties 配置
     * @param remote     remoteService
     * @return 包装后的连接
     */
    public static RedisConnection wrapConnection(RedisConnection connection,
                                                 SpanReportingListener listener,
                                                 InsightProperties properties,
                                                 String remote) {
        if (connection == null || isInsightWrapped(connection)) {
            return connection;
        }
        Class<?>[] ifaces = ClassUtils.getAllInterfaces(connection);
        if (ifaces.length == 0) {
            ifaces = new Class<?>[]{RedisConnection.class};
        }
        ClassLoader cl = connection.getClass().getClassLoader();
        return (RedisConnection) Proxy.newProxyInstance(
                cl,
                ifaces,
                new ConnectionHandler(connection, listener, properties, remote));
    }

    static boolean shouldTraceMethod(Method method) {
        if (method == null) {
            return false;
        }
        String name = method.getName();
        if (SKIP_METHODS.contains(name)) {
            return false;
        }
        Class<?> ret = method.getReturnType();
        if (method.getParameterCount() == 0
                && ret != void.class
                && ret != Void.class
                && (ret.getName().endsWith("Commands") || RedisConnection.class.isAssignableFrom(ret))) {
            return false;
        }
        return true;
    }

    /**
     * 包装 Lettuce 原生连接，使 {@code async()}/{@code sync()} 返回的命令视图可追踪。
     *
     * @param nativeConn 原生连接
     * @param listener   上报
     * @param properties 配置
     * @param remote     remoteService
     * @return 包装后的原生连接；无需包装时原样返回
     * @author 苏渡苇
     * @since 2026-10-21
     */
    static Object wrapNativeConnection(Object nativeConn,
                                       SpanReportingListener listener,
                                       InsightProperties properties,
                                       String remote) {
        if (nativeConn == null || isInsightWrapped(nativeConn)) {
            return nativeConn;
        }
        if (!hasNoArgMethod(nativeConn.getClass(), "async")) {
            return nativeConn;
        }
        Class<?>[] ifaces = ClassUtils.getAllInterfaces(nativeConn);
        if (ifaces.length == 0) {
            return nativeConn;
        }
        ClassLoader cl = nativeConn.getClass().getClassLoader();
        return Proxy.newProxyInstance(
                cl,
                ifaces,
                new NativeConnectionHandler(nativeConn, listener, properties, remote));
    }

    private static boolean hasNoArgMethod(Class<?> type, String name) {
        try {
            type.getMethod(name);
            return true;
        } catch (NoSuchMethodException e) {
            for (Class<?> iface : ClassUtils.getAllInterfacesForClassAsSet(type)) {
                try {
                    iface.getMethod(name);
                    return true;
                } catch (NoSuchMethodException ignored) {
                    // try next
                }
            }
            return false;
        }
    }

    private static Object wrapCommandsView(Object commands,
                                           SpanReportingListener listener,
                                           InsightProperties properties,
                                           String remote,
                                           boolean asyncStyle) {
        if (commands == null || isInsightWrapped(commands)) {
            return commands;
        }
        Class<?>[] ifaces = ClassUtils.getAllInterfaces(commands);
        if (ifaces.length == 0) {
            return commands;
        }
        ClassLoader cl = commands.getClass().getClassLoader();
        return Proxy.newProxyInstance(
                cl,
                ifaces,
                new CommandsHandler(commands, listener, properties, remote, asyncStyle));
    }

    private static final class FactoryHandler implements InvocationHandler {
        private final Object delegate;
        private final SpanReportingListener listener;
        private final InsightProperties properties;
        private final String remote;

        FactoryHandler(Object delegate,
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
            try {
                Object result = method.invoke(delegate, args);
                String name = method.getName();
                // Reactive 连接不包装（追踪范围仍为同步 / Lettuce 原生异步）
                if (REACTIVE_FACTORY_METHODS.contains(name)) {
                    return result;
                }
                if (("getConnection".equals(name) || "getClusterConnection".equals(name))
                        && result instanceof RedisConnection) {
                    return wrapConnection((RedisConnection) result, listener, properties, remote);
                }
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }

    private static final class ConnectionHandler implements InvocationHandler {
        private final RedisConnection delegate;
        private final SpanReportingListener listener;
        private final InsightProperties properties;
        private final String remote;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        ConnectionHandler(RedisConnection delegate,
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
            if ("close".equals(name)) {
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause() != null ? e.getCause() : e;
                } finally {
                    closed.set(true);
                }
            }
            if ("getNativeConnection".equals(name)) {
                try {
                    Object nativeConn = method.invoke(delegate, args);
                    if (closed.get()) {
                        return nativeConn;
                    }
                    return wrapNativeConnection(nativeConn, listener, properties, remote);
                } catch (InvocationTargetException e) {
                    throw e.getCause() != null ? e.getCause() : e;
                }
            }
            if (!shouldTraceMethod(method)) {
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause() != null ? e.getCause() : e;
                }
            }
            String cmd = InsightRedisSupport.commandName(method.getName());
            String key = InsightRedisSupport.extractKey(args);
            return InsightRedisSupport.traceExecute(cmd, key, remote, properties, listener, () -> {
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof Exception) {
                        throw (Exception) cause;
                    }
                    if (cause instanceof Error) {
                        throw (Error) cause;
                    }
                    throw new Exception(cause);
                }
            });
        }
    }

    /**
     * Lettuce StatefulConnection：包装 async/sync 命令视图；reactive 透传。
     */
    private static final class NativeConnectionHandler implements InvocationHandler {
        private final Object delegate;
        private final SpanReportingListener listener;
        private final InsightProperties properties;
        private final String remote;

        NativeConnectionHandler(Object delegate,
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
            try {
                Object result = method.invoke(delegate, args);
                String name = method.getName();
                if ("async".equals(name) && method.getParameterCount() == 0) {
                    return wrapCommandsView(result, listener, properties, remote, true);
                }
                if ("sync".equals(name) && method.getParameterCount() == 0) {
                    return wrapCommandsView(result, listener, properties, remote, false);
                }
                // reactive 视图不包装
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }

    /**
     * Lettuce sync/async 命令视图。
     */
    private static final class CommandsHandler implements InvocationHandler {
        private final Object delegate;
        private final SpanReportingListener listener;
        private final InsightProperties properties;
        private final String remote;
        private final boolean asyncStyle;

        CommandsHandler(Object delegate,
                        SpanReportingListener listener,
                        InsightProperties properties,
                        String remote,
                        boolean asyncStyle) {
            this.delegate = delegate;
            this.listener = listener;
            this.properties = properties;
            this.remote = remote;
            this.asyncStyle = asyncStyle;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("equals".equals(name) || "hashCode".equals(name) || "toString".equals(name)
                    || NATIVE_VIEW_METHODS.contains(name)
                    || "getStatefulConnection".equals(name)
                    || "getConnectionState".equals(name)) {
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause() != null ? e.getCause() : e;
                }
            }
            // 异步风格：仅追踪有返回值的命令（通常为 RedisFuture / CompletionStage）
            if (asyncStyle && (method.getReturnType() == void.class || method.getReturnType() == Void.class)) {
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause() != null ? e.getCause() : e;
                }
            }
            String cmd = InsightRedisSupport.commandName(name);
            String key = InsightRedisSupport.extractKey(args);
            return InsightRedisSupport.traceExecute(cmd, key, remote, properties, listener, () -> {
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof Exception) {
                        throw (Exception) cause;
                    }
                    if (cause instanceof Error) {
                        throw (Error) cause;
                    }
                    throw new Exception(cause);
                }
            });
        }
    }

    /**
     * JedisPool.getResource → wrapJedis。
     */
    private static final class JedisPoolGetResourceInterceptor
            implements org.aopalliance.intercept.MethodInterceptor {

        private final SpanReportingListener listener;
        private final InsightProperties properties;
        private final String remote;

        JedisPoolGetResourceInterceptor(SpanReportingListener listener,
                                        InsightProperties properties,
                                        String remote) {
            this.listener = listener;
            this.properties = properties;
            this.remote = remote;
        }

        @Override
        public Object invoke(org.aopalliance.intercept.MethodInvocation invocation) throws Throwable {
            Object jedis = invocation.proceed();
            return wrapJedis(jedis, listener, properties, remote);
        }
    }

    /**
     * Jedis 命令拦截：追踪业务命令；close 归还后清理代理状态。
     */
    private static final class JedisCommandInterceptor
            implements org.aopalliance.intercept.MethodInterceptor {

        private final SpanReportingListener listener;
        private final InsightProperties properties;
        private final String remote;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        JedisCommandInterceptor(SpanReportingListener listener,
                                InsightProperties properties,
                                String remote) {
            this.listener = listener;
            this.properties = properties;
            this.remote = remote;
        }

        @Override
        public Object invoke(org.aopalliance.intercept.MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            String name = method.getName();
            Object[] args = invocation.getArguments();
            if ("close".equals(name)) {
                try {
                    return invocation.proceed();
                } finally {
                    closed.set(true);
                }
            }
            if (closed.get() || JEDIS_SKIP_METHODS.contains(name)) {
                return invocation.proceed();
            }
            if (method.getParameterCount() == 0
                    && method.getReturnType() != void.class
                    && method.getReturnType() != Void.class
                    && method.getReturnType().getName().endsWith("Commands")) {
                return invocation.proceed();
            }
            String cmd = InsightRedisSupport.commandName(name);
            String key = InsightRedisSupport.extractKey(args);
            return InsightRedisSupport.traceExecute(cmd, key, remote, properties, listener, () -> {
                try {
                    return invocation.proceed();
                } catch (Throwable t) {
                    if (t instanceof Exception) {
                        throw (Exception) t;
                    }
                    if (t instanceof Error) {
                        throw (Error) t;
                    }
                    throw new Exception(t);
                }
            });
        }
    }
}
