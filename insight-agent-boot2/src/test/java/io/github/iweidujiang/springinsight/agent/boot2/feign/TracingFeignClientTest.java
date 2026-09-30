package io.github.iweidujiang.springinsight.agent.boot2.feign;

import feign.Client;
import feign.Request;
import feign.Response;
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
 * {@link TracingFeignClient} remoteService 解析与 W3C 透传单测（Boot2）。
 *
 * @since 2026-09-07
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TracingFeignClientTest {

    @Mock
    private Client delegate;

    @Mock
    private SpanReportingListener spanReportingListener;

    @Mock
    private ObjectProvider<InsightBoot2Properties> propertiesProvider;

    @Mock
    private ObjectProvider<SpanReportingListener> listenerProvider;

    private InsightBoot2Properties properties;
    private TracingFeignClient client;

    /**
     * 初始化可测 Client。
     */
    @BeforeEach
    public void setUp() {
        properties = new InsightBoot2Properties();
        properties.setHttpTracingEnabled(true);
        properties.setHttpTracePropagationEnabled(true);
        properties.setServiceName("boot2-demo-consumer");
        when(propertiesProvider.getIfAvailable()).thenReturn(properties);
        when(listenerProvider.getIfAvailable()).thenReturn(spanReportingListener);
        client = new TracingFeignClient(delegate, propertiesProvider, listenerProvider);
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
     * 校验从 URL 解析 host 作为 remoteService。
     */
    @Test
    public void resolveRemoteService_usesHost() {
        assertEquals("provider", TracingFeignClient.resolveRemoteService("http://provider/api/hello"));
        assertEquals("unknown", TracingFeignClient.resolveRemoteService("not-a-uri"));
    }

    /**
     * 校验 path 提取。
     */
    @Test
    public void safePath_extractsPath() {
        assertEquals("/api/hello", TracingFeignClient.safePath("http://provider/api/hello"));
    }

    /**
     * 默认开启透传：委托请求应带 traceparent。
     */
    @Test
    public void execute_injectsTraceparentWhenEnabled() throws Exception {
        TraceSpan parent = TraceContext.startSpan("GET /call");
        Request inbound = sampleRequest();
        Response ok = Response.builder()
                .status(200)
                .reason("OK")
                .request(inbound)
                .headers(Collections.<String, Collection<String>>emptyMap())
                .build();
        when(delegate.execute(any(Request.class), any(Request.Options.class))).thenReturn(ok);

        client.execute(inbound, new Request.Options());

        ArgumentCaptor<Request> reqCaptor = ArgumentCaptor.forClass(Request.class);
        verify(delegate).execute(reqCaptor.capture(), any(Request.Options.class));
        Collection<String> tpHeaders = reqCaptor.getValue().headers().get(W3cTracePropagator.TRACEPARENT_HEADER);
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
     * 关闭透传时不写 traceparent。
     */
    @Test
    public void execute_skipsTraceparentWhenDisabled() throws Exception {
        properties.setHttpTracePropagationEnabled(false);
        TraceContext.startSpan("GET /call");
        Request inbound = sampleRequest();
        Response ok = Response.builder()
                .status(200)
                .reason("OK")
                .request(inbound)
                .headers(Collections.<String, Collection<String>>emptyMap())
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
                Collections.<String, Collection<String>>emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null);
    }
}
