package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
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

/**
 * Redis {@link RedisConnectionFactory} / {@link RedisConnection} JDK 动态代理。
 * <p>工厂代理保留原 Bean 全部接口（如 Lettuce + Reactive），仅包装同步连接上的命令。</p>
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

    private InsightRedisProxies() {
    }

    /**
     * 是否已由本类包装。
     *
     * @param bean 候选 Bean
     * @return true 表示已是本代理
     */
    public static boolean isInsightWrapped(Object bean) {
        if (bean == null || !Proxy.isProxyClass(bean.getClass())) {
            return false;
        }
        return Proxy.getInvocationHandler(bean) instanceof FactoryHandler
                || Proxy.getInvocationHandler(bean) instanceof ConnectionHandler;
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
}
