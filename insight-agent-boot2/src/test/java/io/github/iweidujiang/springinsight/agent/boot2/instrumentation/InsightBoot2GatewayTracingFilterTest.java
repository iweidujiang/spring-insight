package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * InsightBoot2GatewayTracingFilter 单测：resolveRemoteService。
 *
 * @since 2026-09-07
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public class InsightBoot2GatewayTracingFilterTest {

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
}
