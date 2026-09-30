package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.context.TraceContext;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * {@link InsightWebClientExchangeFilter} 解析与 W3C 透传单测。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
class InsightWebClientExchangeFilterTest {

    @Mock
    private SpanReportingListener spanReportingListener;

    private InsightProperties properties;
    private InsightWebClientExchangeFilter filter;

    /**
     * 初始化过滤器。
     */
    @BeforeEach
    void setUp() {
        properties = new InsightProperties();
        properties.setHttpTracingEnabled(true);
        properties.setHttpTracePropagationEnabled(true);
        filter = new InsightWebClientExchangeFilter(spanReportingListener, properties);
        TraceContext.clear();
    }

    /**
     * 清理 ThreadLocal。
     */
    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void resolveRemoteService_usesHost() {
        assertEquals("sca-order", InsightWebClientExchangeFilter.resolveRemoteService(
                URI.create("http://sca-order/api/orders")));
        assertEquals("sca-user", InsightWebClientExchangeFilter.resolveRemoteService(
                URI.create("lb://sca-user/users/1")));
        assertEquals("unknown", InsightWebClientExchangeFilter.resolveRemoteService(null));
    }

    @Test
    void compactOp_includesHostPathQuery() {
        assertEquals("sca-order/api/x?a=1", InsightWebClientExchangeFilter.compactOp(
                URI.create("http://sca-order/api/x?a=1")));
    }

    /**
     * 默认开启透传：下游 ExchangeFunction 收到的请求应带 traceparent。
     */
    @Test
    void filter_injectsTraceparentWhenEnabled() {
        TraceSpan parent = TraceContext.startSpan("GET /api");
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction next = request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(200)).build());
        };
        ClientRequest inbound = ClientRequest.create(HttpMethod.GET, URI.create("http://sca-product/p/1")).build();

        filter.filter(inbound, next).block();

        ClientRequest sent = captured.get();
        assertNotNull(sent);
        String tp = sent.headers().getFirst(W3cTracePropagator.TRACEPARENT_HEADER);
        assertNotNull(tp);
        assertTrue(tp.startsWith("00-"));

        ArgumentCaptor<TraceSpan> spanCaptor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(spanCaptor.capture());
        TraceSpan reported = spanCaptor.getValue();
        assertEquals("CLIENT", reported.getSpanKind());
        assertEquals("WebClient", reported.getComponent());
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
        TraceContext.startSpan("GET /api");
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction next = request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(200)).build());
        };
        ClientRequest inbound = ClientRequest.create(HttpMethod.GET, URI.create("http://host/x")).build();

        filter.filter(inbound, next).block();

        assertNull(captured.get().headers().getFirst(W3cTracePropagator.TRACEPARENT_HEADER));
        ArgumentCaptor<TraceSpan> spanCaptor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(spanCaptor.capture());
        assertNull(spanCaptor.getValue().getTags().get("insight.propagation"));
    }
}
