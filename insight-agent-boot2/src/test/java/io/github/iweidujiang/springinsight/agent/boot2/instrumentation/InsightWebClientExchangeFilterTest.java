package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.boot2.context.W3cTracePropagator;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
 * {@link InsightWebClientExchangeFilter} remoteService / W3C 透传单测（Boot2）。
 *
 * @since 2026-09-07
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class InsightWebClientExchangeFilterTest {

    @Mock
    private SpanReportingListener spanReportingListener;

    private InsightBoot2Properties properties;
    private InsightWebClientExchangeFilter filter;

    /**
     * 初始化过滤器。
     */
    @BeforeEach
    public void setUp() {
        properties = new InsightBoot2Properties();
        properties.setHttpTracingEnabled(true);
        properties.setHttpTracePropagationEnabled(true);
        properties.setServiceName("boot2-demo-consumer");
        filter = new InsightWebClientExchangeFilter(spanReportingListener, properties);
        TraceContext.clear();
    }

    /**
     * 清理 ThreadLocal。
     */
    @AfterEach
    public void tearDown() {
        TraceContext.clear();
    }

    /**
     * 校验 host / lb:// 解析。
     */
    @Test
    public void resolveRemoteService_usesHost() {
        assertEquals("sca-order", InsightWebClientExchangeFilter.resolveRemoteService(
                URI.create("http://sca-order/api/orders")));
        assertEquals("sca-user", InsightWebClientExchangeFilter.resolveRemoteService(
                URI.create("lb://sca-user/users/1")));
        assertEquals("unknown", InsightWebClientExchangeFilter.resolveRemoteService(null));
    }

    /**
     * 校验操作名压缩。
     */
    @Test
    public void compactOp_includesHostPathQuery() {
        assertEquals("sca-order/api/x?a=1", InsightWebClientExchangeFilter.compactOp(
                URI.create("http://sca-order/api/x?a=1")));
    }

    /**
     * 默认开启透传：下游应收到 traceparent。
     */
    @Test
    public void filter_injectsTraceparentWhenEnabled() {
        TraceSpan parent = TraceContext.startSpan("GET /api");
        final AtomicReference<ClientRequest> captured = new AtomicReference<ClientRequest>();
        ExchangeFunction next = new ExchangeFunction() {
            @Override
            public Mono<ClientResponse> exchange(ClientRequest request) {
                captured.set(request);
                return Mono.just(ClientResponse.create(HttpStatus.OK).build());
            }
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
    public void filter_skipsTraceparentWhenDisabled() {
        properties.setHttpTracePropagationEnabled(false);
        TraceContext.startSpan("GET /api");
        final AtomicReference<ClientRequest> captured = new AtomicReference<ClientRequest>();
        ExchangeFunction next = new ExchangeFunction() {
            @Override
            public Mono<ClientResponse> exchange(ClientRequest request) {
                captured.set(request);
                return Mono.just(ClientResponse.create(HttpStatus.OK).build());
            }
        };
        ClientRequest inbound = ClientRequest.create(HttpMethod.GET, URI.create("http://host/x")).build();

        filter.filter(inbound, next).block();

        assertNull(captured.get().headers().getFirst(W3cTracePropagator.TRACEPARENT_HEADER));
        ArgumentCaptor<TraceSpan> spanCaptor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(spanCaptor.capture());
        assertNull(spanCaptor.getValue().getTags().get("insight.propagation"));
    }
}
