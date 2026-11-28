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
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * RabbitTemplate 包装：幂等包装与生产方法打点。
 *
 * @author 苏渡苇
 * @since 2026-11-28
 */
@ExtendWith(MockitoExtension.class)
class InsightRabbitProxiesTest {

    @Mock
    private SpanReportingListener listener;

    private InsightProperties properties;

    @BeforeEach
    void setUp() {
        properties = new InsightProperties();
        properties.setEnabled(true);
        properties.setRabbitTracingEnabled(true);
        properties.setRabbitSlowThresholdMs(0L);
        TraceContext.clear();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void wrap_isIdempotent() {
        RabbitTemplate template = new RabbitTemplate();
        RabbitTemplate wrapped = InsightRabbitProxies.wrap(template, listener, properties);
        assertTrue(InsightRabbitProxies.isInsightWrapped(wrapped));
        assertSame(wrapped, InsightRabbitProxies.wrap(wrapped, listener, properties));
    }

    @Test
    void convertAndSend_errorReportsClientSpan() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        doThrow(new AmqpException("no broker")).when(connectionFactory).createConnection();
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        RabbitTemplate wrapped = InsightRabbitProxies.wrap(template, listener, properties);

        TraceContext.startSpan("HTTP POST /order");
        assertThrows(AmqpException.class, () -> wrapped.convertAndSend("orders", "created", "payload"));

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(listener).reportSpan(captor.capture());
        TraceSpan span = captor.getValue();
        assertTrue("CLIENT".equals(span.getSpanKind()));
        assertTrue("RABBITMQ".equals(span.getComponent()));
        assertTrue("RABBIT_ERROR".equals(span.getErrorCode()));
        assertTrue("orders".equals(span.getRemoteService()));
    }
}
