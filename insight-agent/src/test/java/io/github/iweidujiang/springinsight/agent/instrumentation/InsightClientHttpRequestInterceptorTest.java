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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link InsightClientHttpRequestInterceptor} 单测。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsightClientHttpRequestInterceptorTest {

    @Mock
    private SpanReportingListener spanReportingListener;

    private InsightProperties properties;
    private InsightClientHttpRequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new InsightProperties();
        properties.setHttpTracingEnabled(true);
        interceptor = new InsightClientHttpRequestInterceptor(
                spanReportingListener, properties, "RestTemplate");
        TraceContext.clear();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void resolveRemoteService_usesHostIncludingLb() {
        assertEquals("sca-order", InsightClientHttpRequestInterceptor.resolveRemoteService(
                URI.create("http://sca-order/api/orders")));
        assertEquals("order", InsightClientHttpRequestInterceptor.resolveRemoteService(
                URI.create("lb://order/items")));
        assertEquals("unknown", InsightClientHttpRequestInterceptor.resolveRemoteService(null));
    }

    @Test
    void compactOp_includesHostPathQuery() {
        assertEquals("sca-order/api/x?a=1", InsightClientHttpRequestInterceptor.compactOp(
                URI.create("http://sca-order/api/x?a=1")));
    }

    @Test
    void noParent_skipsClientSpan() throws Exception {
        ClientHttpResponse response = mockResponse(HttpStatus.OK);
        ClientHttpRequestExecution execution = (req, body) -> response;

        assertEquals(response, interceptor.intercept(request("http://host/p"), new byte[0], execution));
        verify(spanReportingListener, never()).reportSpan(any());
    }

    @Test
    void httpTracingDisabled_skipsClientSpan() throws Exception {
        properties.setHttpTracingEnabled(false);
        TraceContext.startSpan("parent");
        ClientHttpResponse response = mockResponse(HttpStatus.OK);
        ClientHttpRequestExecution execution = (req, body) -> response;

        interceptor.intercept(request("http://host/p"), new byte[0], execution);
        verify(spanReportingListener, never()).reportSpan(any());
    }

    @Test
    void status200_reportsOkClientSpan() throws Exception {
        TraceSpan parent = TraceContext.startSpan("parent");
        ClientHttpResponse response = mockResponse(HttpStatus.OK);
        AtomicInteger calls = new AtomicInteger();
        ClientHttpRequestExecution execution = (req, body) -> {
            calls.incrementAndGet();
            return response;
        };

        interceptor.intercept(request("http://sca-product/product/price/1?x=1"), new byte[0], execution);

        assertEquals(1, calls.get());
        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(captor.capture());
        TraceSpan reported = captor.getValue();
        assertEquals("CLIENT", reported.getSpanKind());
        assertEquals("RestTemplate", reported.getComponent());
        assertEquals(parent.getTraceId(), reported.getTraceId());
        assertEquals(parent.getSpanId(), reported.getParentSpanId());
        assertEquals("sca-product", reported.getRemoteService());
        assertEquals("/product/price/1", reported.getRemoteEndpoint());
        assertEquals("GET sca-product/product/price/1?x=1", reported.getOperationName());
        assertEquals("200", reported.getTags().get("http.status_code"));
        assertEquals("OK", reported.getStatusCode());
        assertNull(reported.getErrorCode());
        assertTrue(reported.isFinished());
        // 子 Span 未压入 TraceContext
        assertEquals(1, TraceContext.getStackDepth());
    }

    @Test
    void status404_reportsHttpError() throws Exception {
        TraceContext.startSpan("parent");
        ClientHttpResponse response = mockResponse(HttpStatus.NOT_FOUND);
        ClientHttpRequestExecution execution = (req, body) -> response;

        interceptor.intercept(request("http://host/missing"), new byte[0], execution);

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(captor.capture());
        assertEquals("HTTP_404", captor.getValue().getErrorCode());
        assertEquals("ERROR", captor.getValue().getStatusCode());
    }

    @Test
    void ioException_reportsIoErrorAndRethrows() throws Exception {
        TraceContext.startSpan("parent");
        ClientHttpRequestExecution execution = (req, body) -> {
            throw new IOException("connection reset");
        };

        IOException thrown = assertThrows(IOException.class,
                () -> interceptor.intercept(request("http://host/x"), new byte[0], execution));
        assertEquals("connection reset", thrown.getMessage());

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(captor.capture());
        assertEquals("IO_ERROR", captor.getValue().getErrorCode());
        assertEquals("connection reset", captor.getValue().getErrorMessage());
    }

    private static HttpRequest request(String uri) {
        HttpRequest request = mock(HttpRequest.class);
        lenient().when(request.getURI()).thenReturn(URI.create(uri));
        lenient().when(request.getMethod()).thenReturn(HttpMethod.GET);
        return request;
    }

    private static ClientHttpResponse mockResponse(HttpStatus status) throws IOException {
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        lenient().when(response.getStatusCode()).thenReturn(status);
        lenient().when(response.getHeaders()).thenReturn(new HttpHeaders());
        return response;
    }
}
