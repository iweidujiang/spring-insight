package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.context.TraceContext;
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
import org.mockito.stubbing.Answer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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

    private InsightBoot2Properties properties;
    private HttpRequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new InsightBoot2Properties();
        properties.setEnabled(true);
        properties.setServiceName("sca-order");
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

        TraceSpan server = new TraceSpan();
        server.setSampled(true);
        server.setOperationName("POST /order/create");
        server.setSpanKind("SERVER");
        when(request.getAttribute("X-Insight-Boot2-Span")).thenReturn(server);

        assertFalse(TraceContext.currentSpan().isPresent());

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
        final Map<String, Object> attrs = new HashMap<String, Object>();
        Answer<Object> attrGet = new Answer<Object>() {
            @Override
            public Object answer(org.mockito.invocation.InvocationOnMock inv) {
                return attrs.get(inv.getArgument(0));
            }
        };
        Answer<Void> attrSet = new Answer<Void>() {
            @Override
            public Void answer(org.mockito.invocation.InvocationOnMock inv) {
                attrs.put(inv.getArgument(0), inv.getArgument(1));
                return null;
            }
        };
        when(request.getAttribute(anyString())).thenAnswer(attrGet);
        doAnswer(attrSet).when(request).setAttribute(anyString(), any());

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/order/create");
        when(request.getQueryString()).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getStatus()).thenReturn(200);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertTrue(TraceContext.currentSpan().isPresent());

        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(spanReportingListener).reportSpan(captor.capture());
        assertEquals("POST /order/create", captor.getValue().getOperationName());
        assertFalse(TraceContext.currentSpan().isPresent());
    }
}
