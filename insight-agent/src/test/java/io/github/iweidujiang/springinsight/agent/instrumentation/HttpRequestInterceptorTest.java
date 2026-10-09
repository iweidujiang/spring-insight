package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HttpRequestInterceptor}：入口 SERVER Span 必须始终上报。
 *
 * @since 2026-10-09
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpRequestInterceptorTest {

    @Mock
    private SpanReportingListener spanReportingListener;

    private InsightProperties properties;
    private HttpRequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new InsightProperties();
        properties.setEnabled(true);
        interceptor = new HttpRequestInterceptor(spanReportingListener, properties);
        TraceContext.clear();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void afterCompletion_reportsServerSpanWhenThreadLocalEmpty() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);
        when(response.getBufferSize()).thenReturn(0);

        TraceSpan server = new TraceSpan();
        server.setSampled(true);
        server.setOperationName("POST /order/create");
        server.setSpanKind("SERVER");
        when(request.getAttribute("X-Trace-Span")).thenReturn(server);
        when(request.getRequestURI()).thenReturn("/order/create");

        assertEquals(0, TraceContext.getStackDepth());

        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(captor.capture());
        TraceSpan reported = captor.getValue();
        assertEquals("POST /order/create", reported.getOperationName());
        assertEquals("SERVER", reported.getSpanKind());
        assertTrue(reported.isFinished());
        assertEquals("OK", reported.getStatusCode());
    }

    @Test
    void afterCompletion_reportsServerSpanWhenStackIntact() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Map<String, Object> attrs = new HashMap<>();
        Answer<Object> attrGet = inv -> attrs.get(inv.getArgument(0));
        Answer<Void> attrSet = inv -> {
            attrs.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        };
        when(request.getAttribute(anyString())).thenAnswer(attrGet);
        org.mockito.Mockito.doAnswer(attrSet).when(request).setAttribute(anyString(), any());

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/order/create");
        when(request.getQueryString()).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("test");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getStatus()).thenReturn(200);
        when(response.getBufferSize()).thenReturn(0);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(1, TraceContext.getStackDepth());

        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(captor.capture());
        assertEquals("POST /order/create", captor.getValue().getOperationName());
        assertEquals(0, TraceContext.getStackDepth());
    }
}
