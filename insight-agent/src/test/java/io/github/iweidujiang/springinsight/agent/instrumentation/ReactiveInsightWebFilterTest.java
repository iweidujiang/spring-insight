package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.context.W3cTracePropagator;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

/**
 * {@link ReactiveInsightWebFilter} 入站 W3C 透传单测。
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
class ReactiveInsightWebFilterTest {

    private static final String SAMPLE_TRACE =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Mock
    private SpanReportingListener spanReportingListener;

    private InsightProperties properties;
    private ReactiveInsightWebFilter filter;

    /**
     * 初始化过滤器。
     */
    @BeforeEach
    void setUp() {
        properties = new InsightProperties();
        properties.setHttpTracingEnabled(true);
        properties.setHttpTracePropagationEnabled(true);
        filter = new ReactiveInsightWebFilter(spanReportingListener, properties);
    }

    /**
     * 入站带合法 traceparent 时延续 TraceId，parent 为上游 SpanId。
     */
    @Test
    void filter_extractsTraceparentWhenEnabled() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://svc/api/hello")
                        .header(W3cTracePropagator.TRACEPARENT_HEADER, SAMPLE_TRACE)
                        .build());
        WebFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange, chain).block();

        ArgumentCaptor<TraceSpan> spanCaptor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(spanCaptor.capture());
        TraceSpan reported = spanCaptor.getValue();
        assertEquals("SERVER", reported.getSpanKind());
        assertEquals("SpringWebFlux", reported.getComponent());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", reported.getTraceId());
        assertEquals("00f067aa0ba902b7", reported.getParentSpanId());
        assertNotEquals("00f067aa0ba902b7", reported.getSpanId());
        assertEquals("w3c", reported.getTags().get("insight.propagation"));
    }

    /**
     * 关闭透传时忽略入站头，新建根 Span。
     */
    @Test
    void filter_ignoresTraceparentWhenDisabled() {
        properties.setHttpTracePropagationEnabled(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://svc/api/hello")
                        .header(W3cTracePropagator.TRACEPARENT_HEADER, SAMPLE_TRACE)
                        .build());
        WebFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange, chain).block();

        ArgumentCaptor<TraceSpan> spanCaptor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(spanCaptor.capture());
        TraceSpan reported = spanCaptor.getValue();
        assertNotEquals("4bf92f3577b34da6a3ce929d0e0e4736", reported.getTraceId());
        assertNull(reported.getParentSpanId());
        assertNull(reported.getTags().get("insight.propagation"));
    }

    /**
     * 无 traceparent 时仍创建根 SERVER Span。
     */
    @Test
    void filter_createsRootWhenHeaderMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://svc/api/hello").build());
        WebFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange, chain).block();

        ArgumentCaptor<TraceSpan> spanCaptor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(spanCaptor.capture());
        TraceSpan reported = spanCaptor.getValue();
        assertEquals("SERVER", reported.getSpanKind());
        assertNull(reported.getParentSpanId());
        assertNull(reported.getTags().get("insight.propagation"));
    }
}
