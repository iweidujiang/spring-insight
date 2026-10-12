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
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisConnectionFactory 代理：连接上的 get 上报 CLIENT Span。
 *
 * @since 2026-10-12
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
class InsightRedisProxiesTest {

    @Mock
    private SpanReportingListener listener;

    private InsightBoot2Properties properties;

    @BeforeEach
    void setUp() {
        properties = new InsightBoot2Properties();
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
        RedisConnectionFactory rawFactory = mock(RedisConnectionFactory.class);
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
        verify(listener, never()).reportSpan(any(TraceSpan.class));
    }
}
