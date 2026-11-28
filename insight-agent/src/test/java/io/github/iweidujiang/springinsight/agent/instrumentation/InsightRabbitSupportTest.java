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
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Rabbit 生产端追踪：目的地解析、慢阈值、错误上报与 traceparent 注入。
 *
 * @author 苏渡苇
 * @since 2026-11-28
 */
@ExtendWith(MockitoExtension.class)
class InsightRabbitSupportTest {

    @Mock
    private SpanReportingListener listener;

    private InsightProperties properties;

    @BeforeEach
    void setUp() {
        properties = new InsightProperties();
        properties.setEnabled(true);
        properties.setRabbitTracingEnabled(true);
        properties.setRabbitSlowThresholdMs(0L);
        properties.setHttpTracePropagationEnabled(true);
        TraceContext.clear();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void resolveExchangeAndRoutingKey() {
        assertEquals("orders", InsightRabbitSupport.resolveExchangeAndRoutingKey(
                new Object[]{"orders", "created", "body"})[0]);
        assertEquals("created", InsightRabbitSupport.resolveExchangeAndRoutingKey(
                new Object[]{"orders", "created", "body"})[1]);
        assertEquals("", InsightRabbitSupport.resolveExchangeAndRoutingKey(
                new Object[]{"rk-only", new Object()})[0]);
        assertEquals("rk-only", InsightRabbitSupport.resolveExchangeAndRoutingKey(
                new Object[]{"rk-only", new Object()})[1]);
    }

    @Test
    void shouldReport_errorAlways_thresholdZeroReportsSuccess() {
        assertTrue(InsightRabbitSupport.shouldReport(properties, 1L, true));
        assertTrue(InsightRabbitSupport.shouldReport(properties, 1L, false));
        properties.setRabbitSlowThresholdMs(500L);
        assertFalse(InsightRabbitSupport.shouldReport(properties, 100L, false));
        assertTrue(InsightRabbitSupport.shouldReport(properties, 500L, false));
    }

    @Test
    void noParent_skipsReport() throws Exception {
        Object out = InsightRabbitSupport.tracePublish(
                "ex", "rk", properties, listener, () -> "ok");
        assertEquals("ok", out);
        verify(listener, never()).reportSpan(any());
        assertEquals(0, TraceContext.getStackDepth());
    }

    @Test
    void flagOff_skipsReport() throws Exception {
        TraceContext.startSpan("HTTP POST /order");
        properties.setRabbitTracingEnabled(false);
        InsightRabbitSupport.tracePublish("ex", "rk", properties, listener, () -> "ok");
        verify(listener, never()).reportSpan(any());
        assertEquals(1, TraceContext.getStackDepth());
    }

    @Test
    void success_reportsClientSpan() throws Exception {
        TraceContext.startSpan("HTTP POST /order");
        InsightRabbitSupport.tracePublish("orders", "created", properties, listener, () -> "ok");
        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        TraceSpan span = captor.getValue();
        assertEquals("CLIENT", span.getSpanKind());
        assertEquals("RABBITMQ", span.getComponent());
        assertEquals("RABBIT PRODUCE orders", span.getOperationName());
        assertEquals("orders", span.getRemoteService());
        assertEquals("rabbitmq", span.getTags().get("messaging.system"));
        assertEquals("publish", span.getTags().get("messaging.operation"));
        assertEquals("orders", span.getTags().get("messaging.destination"));
        assertEquals("created", span.getTags().get("messaging.rabbitmq.routing_key"));
        assertEquals(1, TraceContext.getStackDepth());
        assertEquals(null, InsightRabbitSupport.currentPublishSpan());
    }

    @Test
    void error_reportsWithoutPushingStack() {
        TraceContext.startSpan("HTTP POST /order");
        assertThrows(RuntimeException.class, () ->
                InsightRabbitSupport.tracePublish("ex", "rk", properties, listener, () -> {
                    throw new RuntimeException("broker down");
                }));
        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        TraceSpan span = captor.getValue();
        assertEquals("RABBIT_ERROR", span.getErrorCode());
        assertEquals(1, TraceContext.getStackDepth());
    }

    @Test
    void injectTraceparent_writesHeader() {
        TraceContext.startSpan("HTTP POST /order");
        TraceSpan span = InsightRabbitSupport.beginPublish("ex", "rk", properties).orElseThrow();
        Message message = new Message("hi".getBytes(), new MessageProperties());
        InsightRabbitSupport.injectTraceparent(message, span, properties);
        assertNotNull(message.getMessageProperties().getHeaders().get("traceparent"));
        assertEquals("w3c", span.getTags().get("insight.propagation"));
        InsightRabbitSupport.endPublish(java.util.Optional.of(span), null, properties, listener, System.currentTimeMillis());
    }
}
