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
}
