package io.github.iweidujiang.springinsight.feign;

import feign.Client;
import feign.Request;
import feign.Response;
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
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TracingFeignClient} 出站 Span 与 W3C 透传单测。
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
class TracingFeignClientTest {

    @Mock
    private Client delegate;

    @Mock
    private SpanReportingListener spanReportingListener;

    @Mock
    private ObjectProvider<InsightProperties> propertiesProvider;

    @Mock
    private ObjectProvider<SpanReportingListener> listenerProvider;

    private InsightProperties properties;
    private TracingFeignClient client;

    /**
     * 初始化可测 Client 与公共 mock。
     */
    @BeforeEach
    void setUp() {
        properties = new InsightProperties();
        properties.setHttpTracingEnabled(true);
        properties.setHttpTracePropagationEnabled(true);
        when(propertiesProvider.getIfAvailable()).thenReturn(properties);
        when(listenerProvider.getIfAvailable()).thenReturn(spanReportingListener);
        client = new TracingFeignClient(delegate, propertiesProvider, listenerProvider);
        TraceContext.clear();
    }

    /**
     * 清理 ThreadLocal。
     */
    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    /**
     * 默认开启透传：委托请求应带 traceparent，且 parent-id 为 CLIENT SpanId。
     */
    @Test
    void execute_injectsTraceparentWhenEnabled() throws Exception {
        TraceSpan parent = TraceContext.startSpan("GET /call");
        Request inbound = sampleRequest();
        Response ok = Response.builder()
                .status(200)
                .reason("OK")
                .request(inbound)
                .headers(Collections.emptyMap())
                .build();
        when(delegate.execute(any(Request.class), any(Request.Options.class))).thenReturn(ok);

        client.execute(inbound, new Request.Options());

        ArgumentCaptor<Request> reqCaptor = ArgumentCaptor.forClass(Request.class);
        verify(delegate).execute(reqCaptor.capture(), any(Request.Options.class));
        Request sent = reqCaptor.getValue();
        Collection<String> tpHeaders = sent.headers().get(W3cTracePropagator.TRACEPARENT_HEADER);
        assertNotNull(tpHeaders);
        assertEquals(1, tpHeaders.size());
        String tp = tpHeaders.iterator().next();
        assertTrue(tp.startsWith("00-"));

        ArgumentCaptor<TraceSpan> spanCaptor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(spanCaptor.capture());
        TraceSpan reported = spanCaptor.getValue();
        assertEquals("CLIENT", reported.getSpanKind());
        assertEquals("OpenFeign", reported.getComponent());
        assertEquals(parent.getTraceId(), reported.getTraceId());
        assertEquals("w3c", reported.getTags().get("insight.propagation"));
        assertTrue(tp.contains(reported.getSpanId()));
    }

    /**
     * 关闭透传时不写 traceparent 头。
     */
    @Test
    void execute_skipsTraceparentWhenDisabled() throws Exception {
        properties.setHttpTracePropagationEnabled(false);
        TraceContext.startSpan("GET /call");
        Request inbound = sampleRequest();
        Response ok = Response.builder()
                .status(200)
                .reason("OK")
                .request(inbound)
                .headers(Collections.emptyMap())
                .build();
        when(delegate.execute(any(Request.class), any(Request.Options.class))).thenReturn(ok);

        client.execute(inbound, new Request.Options());

        ArgumentCaptor<Request> reqCaptor = ArgumentCaptor.forClass(Request.class);
        verify(delegate).execute(reqCaptor.capture(), any(Request.Options.class));
        assertNull(reqCaptor.getValue().headers().get(W3cTracePropagator.TRACEPARENT_HEADER));

        ArgumentCaptor<TraceSpan> spanCaptor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(spanCaptor.capture());
        assertNull(spanCaptor.getValue().getTags().get("insight.propagation"));
    }

    /**
     * @return 最小 GET 请求
     */
    private static Request sampleRequest() {
        return Request.create(
                Request.HttpMethod.GET,
                "http://boot2-demo-provider/api/hello",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null);
    }
}
