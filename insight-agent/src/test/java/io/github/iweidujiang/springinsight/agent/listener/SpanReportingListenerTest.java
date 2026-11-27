package io.github.iweidujiang.springinsight.agent.listener;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.micrometer.InsightMicrometerBridge;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SpanReportingListener} 采样丢弃单测。
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SpanReportingListenerTest {

    @Mock
    private AsyncSpanReporter asyncSpanReporter;
    @Mock
    private ObjectProvider<InsightMicrometerBridge> micrometerBridge;

    private SpanReportingListener listener;

    /**
     * 初始化监听器。
     */
    @BeforeEach
    void setUp() {
        when(micrometerBridge.getIfAvailable()).thenReturn(null);
        listener = new SpanReportingListener(asyncSpanReporter, micrometerBridge, new InsightProperties());
    }

    /**
     * 已采样 Span 会交给上报器。
     */
    @Test
    void reportSpan_forwardsWhenSampled() {
        when(asyncSpanReporter.report(any(TraceSpan.class))).thenReturn(true);
        TraceSpan span = new TraceSpan();
        span.setSampled(true);
        span.finish();

        listener.reportSpan(span);

        verify(asyncSpanReporter).report(span);
        assertEquals(1, listener.getStats().getTotalReportedSpans());
        assertEquals(0, listener.getStats().getTotalDroppedUnsampled());
    }

    /**
     * 未采样 Span 直接丢弃。
     */
    @Test
    void reportSpan_dropsWhenUnsampled() {
        TraceSpan span = new TraceSpan();
        span.setSampled(false);
        span.finish();

        listener.reportSpan(span);

        verify(asyncSpanReporter, never()).report(any(TraceSpan.class));
        assertEquals(0, listener.getStats().getTotalReportedSpans());
        assertEquals(1, listener.getStats().getTotalDroppedUnsampled());
    }

    /**
     * 采样率未命中时，错误 Span 默认仍上报，且不改写头部采样标志。
     */
    @Test
    void reportSpan_exportsUnsampledErrorByDefault() {
        when(asyncSpanReporter.report(any(TraceSpan.class))).thenReturn(true);
        TraceSpan span = new TraceSpan();
        span.setSampled(false);
        span.finish("HTTP_500", "HTTP Status: 500");

        listener.reportSpan(span);

        verify(asyncSpanReporter).report(span);
        assertFalse(span.isSampled());
        assertEquals(1, listener.getStats().getTotalReportedSpans());
        assertEquals(0, listener.getStats().getTotalDroppedUnsampled());
    }

    /**
     * 关闭错误强制上报后，未采样的错误也丢弃。
     */
    @Test
    void reportSpan_dropsUnsampledErrorWhenDisabled() {
        InsightProperties properties = new InsightProperties();
        properties.setAlwaysSampleErrors(false);
        listener = new SpanReportingListener(asyncSpanReporter, micrometerBridge, properties);

        TraceSpan span = new TraceSpan();
        span.setSampled(false);
        span.finish("HTTP_500", "HTTP Status: 500");

        listener.reportSpan(span);

        verify(asyncSpanReporter, never()).report(any(TraceSpan.class));
        assertEquals(0, listener.getStats().getTotalReportedSpans());
        assertEquals(1, listener.getStats().getTotalDroppedUnsampled());
    }

    /**
     * 丢弃名单命中后，已采样的成功调用也不上报。
     */
    @Test
    void reportSpan_dropsWhenDropPatternMatches() {
        InsightProperties properties = new InsightProperties();
        properties.setSampleDropPatterns(new String[]{"/internal/**"});
        listener = new SpanReportingListener(asyncSpanReporter, micrometerBridge, properties);

        TraceSpan span = new TraceSpan();
        span.setSampled(true);
        span.setOperationName("GET /internal/ping");
        span.addTag("http.path", "/internal/ping");
        span.finish();

        listener.reportSpan(span);

        verify(asyncSpanReporter, never()).report(any(TraceSpan.class));
        assertEquals(1, listener.getStats().getTotalDroppedUnsampled());
    }

    /**
     * 强制名单命中后，未采样的成功调用仍上报。
     */
    @Test
    void reportSpan_exportsUnsampledSuccessWhenForcePatternMatches() {
        when(asyncSpanReporter.report(any(TraceSpan.class))).thenReturn(true);
        InsightProperties properties = new InsightProperties();
        properties.setSampleForcePatterns(new String[]{"GET /billing/**"});
        listener = new SpanReportingListener(asyncSpanReporter, micrometerBridge, properties);

        TraceSpan span = new TraceSpan();
        span.setSampled(false);
        span.setOperationName("GET /billing/charge");
        span.finish();

        listener.reportSpan(span);

        verify(asyncSpanReporter).report(span);
        assertEquals(1, listener.getStats().getTotalReportedSpans());
    }
}
