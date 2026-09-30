package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.context.W3cTracePropagator;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * InsightBoot2GatewayTracingFilter：remoteService 与 W3C 透传单测。
 *
 * @since 2026-09-07
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class InsightBoot2GatewayTracingFilterTest {

    @Mock
    private SpanReportingListener spanReportingListener;

    private InsightBoot2Properties properties;
    private InsightBoot2GatewayTracingFilter filter;

    /**
     * 初始化过滤器。
     */
    @BeforeEach
    public void setUp() {
        properties = new InsightBoot2Properties();
        properties.setHttpTracingEnabled(true);
        properties.setHttpTracePropagationEnabled(true);
        filter = new InsightBoot2GatewayTracingFilter(spanReportingListener, properties);
    }

    /**
     * lb:// 路由取 host 为服务名。
     */
    @Test
    public void resolveRemoteService_prefersLbRouteHost() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/x").build());
        Route route = mock(Route.class);
        when(route.getUri()).thenReturn(URI.create("lb://boot2-demo-provider"));
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
        assertEquals("boot2-demo-provider", InsightBoot2GatewayTracingFilter.resolveRemoteService(exchange));
    }

    /**
     * http 路由取 host。
     */
    @Test
    public void resolveRemoteService_usesHttpRouteHost() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/x").build());
        Route route = mock(Route.class);
        when(route.getUri()).thenReturn(URI.create("http://127.0.0.1:18091"));
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
        assertEquals("127.0.0.1", InsightBoot2GatewayTracingFilter.resolveRemoteService(exchange));
    }

    /**
     * 无路由时回退 GATEWAY_REQUEST_URL。
     */
    @Test
    public void resolveRemoteService_fallsBackToRequestUrl() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/x").build());
        exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("http://orders.internal/v1"));
        assertEquals("orders.internal", InsightBoot2GatewayTracingFilter.resolveRemoteService(exchange));
    }

    /**
     * 无任何信息时为 unknown。
     */
    @Test
    public void resolveRemoteService_unknownWhenMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/x").build());
        assertEquals("unknown", InsightBoot2GatewayTracingFilter.resolveRemoteService(exchange));
    }

    /**
     * 默认开启透传：下游请求应带 traceparent，且与 CLIENT Span 对齐。
     */
    @Test
    public void filter_injectsTraceparentWhenEnabled() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/api/hello").build());
        TraceSpan parent = new TraceSpan();
        parent.setOperationName("GET /api/hello");
        exchange.getAttributes().put(ReactiveInsightWebFilter.SPAN_EXCHANGE_ATTR, parent);

        AtomicReference<ServerWebExchange> captured = new AtomicReference<ServerWebExchange>();
        GatewayFilterChain chain = new GatewayFilterChain() {
            @Override
            public Mono<Void> filter(ServerWebExchange ex) {
                captured.set(ex);
                return Mono.empty();
            }
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
    public void filter_skipsTraceparentWhenDisabled() {
        properties.setHttpTracePropagationEnabled(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/api/hello").build());
        exchange.getAttributes().put(ReactiveInsightWebFilter.SPAN_EXCHANGE_ATTR, new TraceSpan());

        AtomicReference<ServerWebExchange> captured = new AtomicReference<ServerWebExchange>();
        GatewayFilterChain chain = new GatewayFilterChain() {
            @Override
            public Mono<Void> filter(ServerWebExchange ex) {
                captured.set(ex);
                return Mono.empty();
            }
        };

        filter.filter(exchange, chain).block();

        assertNull(captured.get().getRequest().getHeaders().getFirst(W3cTracePropagator.TRACEPARENT_HEADER));
        ArgumentCaptor<TraceSpan> spanCaptor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(spanCaptor.capture());
        assertNull(spanCaptor.getValue().getTags().get("insight.propagation"));
    }
}
