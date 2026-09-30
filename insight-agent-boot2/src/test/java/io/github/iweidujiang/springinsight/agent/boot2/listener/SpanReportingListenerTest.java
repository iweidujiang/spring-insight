package io.github.iweidujiang.springinsight.agent.boot2.listener;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.boot2.micrometer.InsightMicrometerBridge;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SpanReportingListener} 采样丢弃单测（Boot2）。
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SpanReportingListenerTest {

    @Mock
    private AsyncSpanReporter asyncSpanReporter;
    @Mock
    private ObjectProvider<InsightMicrometerBridge> micrometerBridge;

    private SpanReportingListener listener;

    /**
     * 初始化。
     */
    @BeforeEach
    public void setUp() {
        when(micrometerBridge.getIfAvailable()).thenReturn(null);
        listener = new SpanReportingListener(asyncSpanReporter, micrometerBridge, new InsightBoot2Properties());
    }

    /**
     * 已采样会上报。
     */
    @Test
    public void reportSpan_forwardsWhenSampled() {
        when(asyncSpanReporter.report(any(TraceSpan.class))).thenReturn(true);
        TraceSpan span = new TraceSpan();
        span.setSampled(true);
        span.finish();
        listener.reportSpan(span);
        verify(asyncSpanReporter).report(span);
    }

    /**
     * 未采样丢弃。
     */
    @Test
    public void reportSpan_dropsWhenUnsampled() {
        TraceSpan span = new TraceSpan();
        span.setSampled(false);
        span.finish();
        listener.reportSpan(span);
        verify(asyncSpanReporter, never()).report(any(TraceSpan.class));
        assertEquals(1L, listener.getTotalDroppedUnsampled());
    }
}
