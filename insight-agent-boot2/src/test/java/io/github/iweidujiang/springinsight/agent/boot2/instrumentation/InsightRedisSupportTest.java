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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Redis 追踪：命令名、key、慢阈值与错误上报。
 *
 * @since 2026-10-12
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
class InsightRedisSupportTest {

    @Mock
    private SpanReportingListener listener;

    private InsightBoot2Properties properties;

    @BeforeEach
    void setUp() {
        properties = new InsightBoot2Properties();
        properties.setEnabled(true);
        properties.setRedisTracingEnabled(true);
        properties.setRedisSlowThresholdMs(500L);
        TraceContext.clear();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void commandNameAndKey() {
        assertEquals("GET", InsightRedisSupport.commandName("get"));
        assertEquals("HGET", InsightRedisSupport.commandName("hGet"));
        assertEquals("SETEX", InsightRedisSupport.commandName("setEx"));
        assertEquals("user:1", InsightRedisSupport.extractKey(new Object[]{"user:1".getBytes(), "v"}));
        assertEquals("order:9", InsightRedisSupport.extractKey(new Object[]{"order:9"}));
        String longKey = "k".repeat(200);
        assertTrue(InsightRedisSupport.truncateKey(longKey).endsWith("…"));
    }

    @Test
    void shouldReport_errorAlways_slowByThreshold() {
        assertTrue(InsightRedisSupport.shouldReport(properties, 1L, true));
        assertFalse(InsightRedisSupport.shouldReport(properties, 100L, false));
        assertTrue(InsightRedisSupport.shouldReport(properties, 500L, false));
        properties.setRedisSlowThresholdMs(0L);
        assertTrue(InsightRedisSupport.shouldReport(properties, 1L, false));
    }

    @Test
    void resolveRemote_hostPortAndLoopback() {
        assertEquals("cache.internal:6379", InsightRedisSupport.resolveRemote(new FakeFactory("cache.internal", 6379)));
        assertEquals("redis", InsightRedisSupport.resolveRemote(new FakeFactory("127.0.0.1", 6379)));
        assertEquals("redis", InsightRedisSupport.resolveRemote(null));
    }

    @Test
    void noParent_skipsReport() throws Exception {
        Object out = InsightRedisSupport.traceExecute(
                "GET", "k", "redis:6379", properties, listener, () -> "ok");
        assertEquals("ok", out);
        verify(listener, never()).reportSpan(any());
        assertEquals(0, TraceContext.getStackDepth());
    }

    @Test
    void fastSuccess_skipsReport() throws Exception {
        TraceContext.startSpan("HTTP GET /x");
        InsightRedisSupport.traceExecute("GET", "k", "redis", properties, listener, () -> "v");
        verify(listener, never()).reportSpan(any());
        assertEquals(1, TraceContext.getStackDepth());
    }

    @Test
    void error_reportsWithoutPushingStack() {
        TraceContext.startSpan("HTTP GET /x");
        assertThrows(RuntimeException.class, () ->
                InsightRedisSupport.traceExecute("SET", "k", "redis", properties, listener, () -> {
                    throw new RuntimeException("boom");
                }));
        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        TraceSpan span = captor.getValue();
        assertEquals("CLIENT", span.getSpanKind());
        assertEquals("REDIS", span.getComponent());
        assertEquals("REDIS SET", span.getOperationName());
        assertEquals("REDIS_ERROR", span.getErrorCode());
        assertEquals(1, TraceContext.getStackDepth());
    }

    @Test
    void completionStage_defersReportUntilDone_andRestoresTraceOnCallback() throws Exception {
        properties.setRedisSlowThresholdMs(0L);
        TraceContext.startSpan("HTTP GET /async");
        CompletableFuture<String> future = new CompletableFuture<>();

        CompletionStage<String> stage = InsightRedisSupport.traceExecute(
                "GET", "k", "redis", properties, listener, () -> future);

        verify(listener, never()).reportSpan(any());
        assertEquals(1, TraceContext.getStackDepth());

        future.complete("ok");
        assertEquals("ok", stage.toCompletableFuture().get(2, TimeUnit.SECONDS));

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        assertEquals("REDIS GET", captor.getValue().getOperationName());
        assertEquals(1, TraceContext.getStackDepth());
    }

    /** 反射用假工厂：提供 getHostName / getPort */
    @SuppressWarnings("unused")
    static final class FakeFactory {
        private final String hostName;
        private final int port;

        FakeFactory(String hostName, int port) {
            this.hostName = hostName;
            this.port = port;
        }

        public String getHostName() {
            return hostName;
        }

        public int getPort() {
            return port;
        }
    }
}
