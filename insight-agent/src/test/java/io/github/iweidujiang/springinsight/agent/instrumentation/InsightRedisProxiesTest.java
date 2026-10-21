package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisConnectionFactory / JedisPool 代理：同步命令上报；Reactive 透传；Lettuce 异步 CompletionStage 延后结束。
 *
 * @since 2026-10-12
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
class InsightRedisProxiesTest {

    @Mock
    private SpanReportingListener listener;

    private InsightProperties properties;

    @BeforeEach
    void setUp() {
        properties = new InsightProperties();
        properties.setEnabled(true);
        properties.setRedisTracingEnabled(true);
        properties.setRedisSlowThresholdMs(0L);
        TraceContext.clear();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void getConnectionGet_reportsClientSpan() {
        RedisConnectionFactory rawFactory = mock(RedisConnectionFactory.class, "cache.internal");
        RedisConnection rawConn = mock(RedisConnection.class);
        when(rawFactory.getConnection()).thenReturn(rawConn);
        when(rawConn.get("user:1".getBytes())).thenReturn("v".getBytes());

        TraceContext.startSpan("HTTP GET /orders");
        Object wrappedFactory = InsightRedisProxies.wrapFactory(rawFactory, listener, properties);
        assertTrue(wrappedFactory instanceof RedisConnectionFactory);
        RedisConnection wrapped = ((RedisConnectionFactory) wrappedFactory).getConnection();
        byte[] value = wrapped.get("user:1".getBytes());
        assertEquals("v", new String(value));

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        TraceSpan span = captor.getValue();
        assertEquals("CLIENT", span.getSpanKind());
        assertEquals("REDIS", span.getComponent());
        assertEquals("REDIS GET", span.getOperationName());
        assertEquals("user:1", span.getTags().get("redis.key"));
        assertEquals(1, TraceContext.getStackDepth());
        verify(rawConn).get("user:1".getBytes());
    }

    @Test
    void close_skipsTrace() {
        RedisConnection raw = mock(RedisConnection.class);
        TraceContext.startSpan("HTTP GET /x");
        RedisConnection wrapped = InsightRedisProxies.wrapConnection(raw, listener, properties, "redis");
        wrapped.close();
        verify(raw).close();
        verify(listener, never()).reportSpan(org.mockito.ArgumentMatchers.any());
    }

    /**
     * Lettuce 同步路径：工厂同时实现 Reactive 接口时，同步 get 仍上报；Reactive 连接原样透传。
     */
    @Test
    void lettuceSyncApi_reportsClientSpan_andReactivePassthrough() {
        LettuceLikeFactory rawFactory = mock(LettuceLikeFactory.class);
        RedisConnection rawConn = mock(RedisConnection.class);
        ReactiveRedisConnection reactiveConn = mock(ReactiveRedisConnection.class);
        when(rawFactory.getConnection()).thenReturn(rawConn);
        when(rawFactory.getReactiveConnection()).thenReturn(reactiveConn);
        when(rawConn.get("cache:1".getBytes())).thenReturn("ok".getBytes());

        TraceContext.startSpan("HTTP GET /lettuce");
        Object wrappedFactory = InsightRedisProxies.wrapFactory(rawFactory, listener, properties);
        assertInstanceOf(RedisConnectionFactory.class, wrappedFactory);
        assertInstanceOf(ReactiveRedisConnectionFactory.class, wrappedFactory);

        assertSame(reactiveConn,
                ((ReactiveRedisConnectionFactory) wrappedFactory).getReactiveConnection());

        RedisConnection wrapped = ((RedisConnectionFactory) wrappedFactory).getConnection();
        assertEquals("ok", new String(wrapped.get("cache:1".getBytes())));

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        assertEquals("REDIS GET", captor.getValue().getOperationName());
        assertEquals("cache:1", captor.getValue().getTags().get("redis.key"));
        verify(listener, org.mockito.Mockito.times(1)).reportSpan(org.mockito.ArgumentMatchers.any());
    }

    /**
     * Lettuce 6 异步：原生 async 命令返回 CompletionStage 时，Span 在完成回调中结束且 Trace 快照可恢复。
     */
    @Test
    void lettuceAsyncApi_defersSpanUntilCompletionStageDone() throws Exception {
        LettuceNative nativeConn = mock(LettuceNative.class);
        LettuceAsyncCommands asyncCmds = mock(LettuceAsyncCommands.class);
        when(nativeConn.async()).thenReturn(asyncCmds);
        CompletableFuture<String> future = new CompletableFuture<>();
        when(asyncCmds.get("async:key")).thenReturn(future);

        RedisConnection rawConn = mock(RedisConnection.class);
        when(rawConn.getNativeConnection()).thenReturn(nativeConn);

        TraceContext.startSpan("HTTP GET /async");
        RedisConnection wrapped = InsightRedisProxies.wrapConnection(rawConn, listener, properties, "redis");
        Object wrappedNative = wrapped.getNativeConnection();
        assertTrue(Proxy.isProxyClass(wrappedNative.getClass()));
        assertTrue(InsightRedisProxies.isInsightWrapped(wrappedNative));

        Method asyncMethod = wrappedNative.getClass().getMethod("async");
        Object wrappedAsync = asyncMethod.invoke(wrappedNative);
        Method getMethod = wrappedAsync.getClass().getMethod("get", String.class);
        @SuppressWarnings("unchecked")
        CompletionStage<String> stage = (CompletionStage<String>) getMethod.invoke(wrappedAsync, "async:key");

        verify(listener, never()).reportSpan(org.mockito.ArgumentMatchers.any());

        future.complete("value");
        assertEquals("value", stage.toCompletableFuture().get(2, TimeUnit.SECONDS));

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        TraceSpan span = captor.getValue();
        assertEquals("REDIS GET", span.getOperationName());
        assertEquals("async:key", span.getTags().get("redis.key"));
        assertEquals(1, TraceContext.getStackDepth());
    }

    /**
     * Jedis 连接池：getResource 返回包装连接；基本 get 上报；close 归还后代理标记关闭。
     */
    @Test
    void jedisPool_getResource_tracesBasicOps_andCleansOnClose() {
        JedisPool pool = mock(JedisPool.class);
        Jedis jedis = mock(Jedis.class);
        when(pool.getResource()).thenReturn(jedis);
        when(jedis.get("user:1")).thenReturn("v");

        TraceContext.startSpan("HTTP GET /jedis");
        Object wrappedPool = InsightRedisProxies.wrapJedisPool(pool, listener, properties);
        assertTrue(InsightRedisProxies.isInsightWrapped(wrappedPool));
        assertInstanceOf(JedisPool.class, wrappedPool);

        Jedis resource = ((JedisPool) wrappedPool).getResource();
        assertTrue(InsightRedisProxies.isInsightWrapped(resource));
        assertInstanceOf(Jedis.class, resource);
        assertEquals("v", resource.get("user:1"));

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        assertEquals("REDIS GET", captor.getValue().getOperationName());
        assertEquals("user:1", captor.getValue().getTags().get("redis.key"));

        resource.close();
        verify(jedis).close();

        // 归还后再次命令仍转发，但已标记 closed（不再上报）
        when(jedis.get("user:2")).thenReturn("x");
        assertEquals("x", resource.get("user:2"));
        verify(listener, org.mockito.Mockito.times(1)).reportSpan(org.mockito.ArgumentMatchers.any());
    }

    /** 模拟 LettuceConnectionFactory：同步 + Reactive 双接口。 */
    interface LettuceLikeFactory extends RedisConnectionFactory, ReactiveRedisConnectionFactory {
    }

    /** 模拟 Lettuce StatefulRedisConnection。 */
    interface LettuceNative {
        LettuceAsyncCommands async();
    }

    /** 模拟 Lettuce RedisAsyncCommands（返回 CompletionStage）。 */
    interface LettuceAsyncCommands {
        CompletionStage<String> get(String key);
    }
}
