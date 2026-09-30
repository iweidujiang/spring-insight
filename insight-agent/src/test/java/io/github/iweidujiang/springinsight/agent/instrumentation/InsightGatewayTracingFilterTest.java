package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.context.W3cTracePropagator;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * {@link InsightGatewayTracingFilter} remoteService 与 W3C 透传单测。
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
class InsightGatewayTracingFilterTest {

    @Mock
    private SpanReportingListener spanReportingListener;

    private InsightProperties properties;
    private InsightGatewayTracingFilter filter;

    /**
     * 初始化过滤器。
     */
    @BeforeEach
    void setUp() {
        properties = new InsightProperties();
        properties.setHttpTracingEnabled(true);
        properties.setHttpTracePropagationEnabled(true);
        filter = new InsightGatewayTracingFilter(spanReportingListener, properties);
    }

    /**
     * 无状态清理（本类未使用 TraceContext 栈）。
     */
    @AfterEach
    void tearDown() {
        // no-op
    }

    /**
     * lb:// 路由取服务名。
     */
    @Test
    void resolveRemoteService_usesLbHost() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/order/1").build());
        Route route = Route.async()
                .id("order")
                .uri(URI.create("lb://sca-order"))
                .predicate(ex -> true)
                .build();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
        assertEquals("sca-order", InsightGatewayTracingFilter.resolveRemoteService(exchange));
    }

    /**
     * 默认开启透传：下游请求应带 traceparent，且与 CLIENT Span 对齐。
     */
    @Test
    void filter_injectsTraceparentWhenEnabled() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/api/hello").build());
        TraceSpan parent = new TraceSpan();
        parent.setOperationName("GET /api/hello");
        exchange.getAttributes().put(ReactiveInsightWebFilter.SPAN_EXCHANGE_ATTR, parent);

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        ServerWebExchange sent = captured.get();
        assertNotNull(sent);
        String tp = sent.getRequest().getHeaders().getFirst(W3cTracePropagator.TRACEPARENT_HEADER);
        assertNotNull(tp);
        assertTrue(tp.startsWith("00-"));

        ArgumentCaptor<TraceSpan> spanCaptor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(spanCaptor.capture());
        TraceSpan reported = spanCaptor.getValue();
        assertEquals("CLIENT", reported.getSpanKind());
        assertEquals("SpringCloudGateway", reported.getComponent());
        assertEquals(parent.getTraceId(), reported.getTraceId());
        assertEquals("w3c", reported.getTags().get("insight.propagation"));
        assertTrue(tp.contains(reported.getSpanId()));
    }

    /**
     * 关闭透传时不写 traceparent。
     */
    @Test
    void filter_skipsTraceparentWhenDisabled() {
        properties.setHttpTracePropagationEnabled(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/api/hello").build());
        exchange.getAttributes().put(ReactiveInsightWebFilter.SPAN_EXCHANGE_ATTR, new TraceSpan());

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertNull(captured.get().getRequest().getHeaders().getFirst(W3cTracePropagator.TRACEPARENT_HEADER));
        ArgumentCaptor<TraceSpan> spanCaptor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(spanCaptor.capture());
        assertNull(spanCaptor.getValue().getTags().get("insight.propagation"));
    }
}
