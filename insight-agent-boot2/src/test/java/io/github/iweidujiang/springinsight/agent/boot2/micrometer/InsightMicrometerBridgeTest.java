package io.github.iweidujiang.springinsight.agent.boot2.micrometer;

import io.github.iweidujiang.springinsight.agent.boot2.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import io.github.iweidujiang.springinsight.agent.boot2.sink.InsightBatchSink;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InsightMicrometerBridge 单测：Counter / Timer 记录。
 *
 * @since 2026-09-07
 * @author 苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
public class InsightMicrometerBridgeTest {

    /**
     * 验证 recordSpan 会增加 accepted 并注册 span Timer。
     */
    @Test
    public void recordSpan_incrementsCountersAndTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();
        final InsightBatchSink sink = new InsightBatchSink() {
            @Override
            public void acceptTraceSpans(List<TraceSpan> spans) {
                // no-op
            }
        };
        ObjectProvider<InsightBatchSink> sinkProvider = new ObjectProvider<InsightBatchSink>() {
            @Override
            public InsightBatchSink getObject() {
                return sink;
            }

            @Override
            public InsightBatchSink getObject(Object... args) {
                return sink;
            }

            @Override
            public InsightBatchSink getIfAvailable() {
                return sink;
            }

            @Override
            public InsightBatchSink getIfUnique() {
                return sink;
            }

            @Override
            public Stream<InsightBatchSink> stream() {
                return Stream.of(sink);
            }

            @Override
            public Stream<InsightBatchSink> orderedStream() {
                return Stream.of(sink);
            }

            @Override
            public Iterator<InsightBatchSink> iterator() {
                return Collections.singletonList(sink).iterator();
            }
        };
        AsyncSpanReporter reporter = new AsyncSpanReporter("test", sinkProvider);
        InsightMicrometerBridge bridge = new InsightMicrometerBridge(registry, reporter);

        TraceSpan span = new TraceSpan();
        span.setSpanKind("CLIENT");
        span.setRemoteService("boot2-demo-provider");
        span.finish();

        bridge.recordSpan(span, true);

        assertEquals(1.0, registry.get("spring.insight.spans.accepted").counter().count(), 0.001);
        assertTrue(registry.find("spring.insight.span")
                .tag("span.kind", "CLIENT")
                .tag("remote.service", "boot2-demo-provider")
                .timer()
                .count() >= 1);
    }
}
