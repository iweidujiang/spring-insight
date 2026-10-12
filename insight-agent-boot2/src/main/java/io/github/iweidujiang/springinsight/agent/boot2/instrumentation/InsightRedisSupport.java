package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/**
 * Redis CLIENT Span 辅助：命令名、key 截断、慢阈值、上报（不压入 Trace 栈）。
 *
 * @since 2026-10-12
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class InsightRedisSupport {

    private static final int KEY_MAX_LEN = 128;

    private InsightRedisSupport() {
    }

    /**
     * @param methodName RedisConnection 方法名（如 {@code hGet}）
     * @return 大写命令名（如 {@code HGET}）
     */
    public static String commandName(String methodName) {
        if (!StringUtils.hasText(methodName)) {
            return "UNKNOWN";
        }
        StringBuilder sb = new StringBuilder(methodName.length());
        for (int i = 0; i < methodName.length(); i++) {
            char c = methodName.charAt(i);
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    /**
     * 从命令参数提取首个 key（String / byte[] / Collection 首元素）。
     *
     * @param args 方法参数
     * @return 截断后的 key；无法识别时为空串
     */
    public static String extractKey(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof byte[]) {
                return truncateKey(new String((byte[]) arg, StandardCharsets.UTF_8));
            }
            if (arg instanceof String) {
                return truncateKey((String) arg);
            }
            if (arg instanceof Collection) {
                Collection<?> col = (Collection<?>) arg;
                if (!col.isEmpty()) {
                    Object first = col.iterator().next();
                    if (first instanceof byte[]) {
                        return truncateKey(new String((byte[]) first, StandardCharsets.UTF_8));
                    }
                    if (first != null) {
                        return truncateKey(String.valueOf(first));
                    }
                }
            }
            if (arg instanceof byte[][]) {
                byte[][] keys = (byte[][]) arg;
                if (keys.length > 0 && keys[0] != null) {
                    return truncateKey(new String(keys[0], StandardCharsets.UTF_8));
                }
            }
        }
        return "";
    }

    /**
     * @param key 原始 key
     * @return 截断后的展示用 key
     */
    public static String truncateKey(String key) {
        if (key == null) {
            return "";
        }
        String compact = key.replaceAll("\\s+", " ").trim();
        if (compact.length() <= KEY_MAX_LEN) {
            return compact;
        }
        return compact.substring(0, KEY_MAX_LEN) + "…";
    }

    /**
     * @param properties 配置
     * @param durationMs 耗时
     * @param error      是否异常结束
     * @return 是否应上报
     */
    public static boolean shouldReport(InsightBoot2Properties properties, long durationMs, boolean error) {
        if (error) {
            return true;
        }
        long threshold = properties.getRedisSlowThresholdMs();
        if (threshold <= 0L) {
            return true;
        }
        return durationMs >= threshold;
    }

    /**
     * 从 RedisConnectionFactory（或 Lettuce/Jedis 实现）解析短 {@code remoteService}。
     * <p>优先反射 {@code getHostName}/{@code getPort}；本机 IP 归一为 {@code redis}。</p>
     *
     * @param factory 连接工厂，可为 null
     * @return remoteService
     */
    public static String resolveRemote(Object factory) {
        if (factory == null) {
            return "redis";
        }
        try {
            Method hostMethod = findNoArg(factory.getClass(), "getHostName", "getHost");
            Method portMethod = findNoArg(factory.getClass(), "getPort");
            if (hostMethod != null) {
                Object host = hostMethod.invoke(factory);
                if (host != null && StringUtils.hasText(String.valueOf(host))) {
                    String h = String.valueOf(host).trim();
                    if (isLoopback(h)) {
                        return "redis";
                    }
                    if (portMethod != null) {
                        Object port = portMethod.invoke(factory);
                        if (port instanceof Number && ((Number) port).intValue() > 0) {
                            return h + ":" + ((Number) port).intValue();
                        }
                    }
                    return h;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "redis";
    }

    private static boolean isLoopback(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return "127.0.0.1".equals(h) || "localhost".equals(h) || "::1".equals(h)
                || "0:0:0:0:0:0:0:1".equals(h);
    }

    private static Method findNoArg(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // try next
            }
        }
        Class<?> c = type.getSuperclass();
        while (c != null && c != Object.class) {
            for (String name : names) {
                try {
                    return c.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                    // try next
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * 执行 Redis 命令并按需上报 CLIENT Span（无父 Span 时直通、不上报）。
     *
     * @param command  命令名
     * @param key      可选 key
     * @param remote   remoteService
     * @param props    配置
     * @param listener 上报
     * @param action   实际调用
     * @param <T>      返回类型
     * @return 调用结果
     * @throws Exception 透传
     */
    public static <T> T traceExecute(String command,
                                     String key,
                                     String remote,
                                     InsightBoot2Properties props,
                                     SpanReportingListener listener,
                                     RedisCallable<T> action) throws Exception {
        if (props == null || !props.isEnabled() || !props.isRedisTracingEnabled()) {
            return action.call();
        }
        Optional<TraceSpan> parentOpt = TraceContext.currentSpan();
        if (!parentOpt.isPresent()) {
            return action.call();
        }

        TraceSpan parent = parentOpt.get();
        String cmd = StringUtils.hasText(command) ? command : "UNKNOWN";
        TraceSpan span = new TraceSpan(parent.getTraceId(), parent.getSpanId());
        span.setSampled(parent.isSampled());
        span.setSpanKind("CLIENT");
        span.setComponent("REDIS");
        span.setOperationName("REDIS " + cmd);
        String remoteName = StringUtils.hasText(remote) ? remote : "redis";
        span.setRemoteService(remoteName);
        span.addTag("db.system", "redis");
        span.addTag("redis.command", cmd);
        if (StringUtils.hasText(key)) {
            span.addTag("redis.key", key);
            span.setRemoteEndpoint(key.length() > 120 ? key.substring(0, 120) + "…" : key);
        }

        long start = System.currentTimeMillis();
        try {
            T result = action.call();
            long duration = Math.max(0L, System.currentTimeMillis() - start);
            if (!shouldReport(props, duration, false)) {
                return result;
            }
            if (duration >= props.getRedisSlowThresholdMs() && props.getRedisSlowThresholdMs() > 0L) {
                span.addTag("redis.slow", "true");
            }
            span.finish();
            report(listener, span);
            return result;
        } catch (Exception e) {
            span.finish("REDIS_ERROR", e.getMessage());
            report(listener, span);
            throw e;
        }
    }

    private static void report(SpanReportingListener listener, TraceSpan span) {
        if (listener != null && span.isSampled()) {
            listener.reportSpan(TraceSpan.snapshot(span));
        }
    }

    /**
     * 可抛受检异常的 Redis 调用。
     *
     * @param <T> 返回类型
     */
    @FunctionalInterface
    public interface RedisCallable<T> {
        /**
         * @return 调用结果
         * @throws Exception Redis 或业务异常
         */
        T call() throws Exception;
    }
}
