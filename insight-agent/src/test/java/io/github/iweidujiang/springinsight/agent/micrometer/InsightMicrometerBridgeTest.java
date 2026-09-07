package io.github.iweidujiang.springinsight.agent.micrometer;

import io.github.iweidujiang.springinsight.agent.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.model.JvmMetric;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.agent.sink.InsightBatchSink;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightMicrometerBridgeTest {

    @Test
    void recordSpan_incrementsCountersAndTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();
        InsightBatchSink sink = new InsightBatchSink() {
            @Override
            public void acceptTraceSpans(List<TraceSpan> spans) {
            }

            @Override
            public void acceptJvmMetrics(List<JvmMetric> metrics) {
            }
        };
        ObjectProvider<InsightBatchSink> sinkProvider = new ObjectProvider<>() {
            @Override
            public InsightBatchSink getObject() {
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
        };
        AsyncSpanReporter reporter = new AsyncSpanReporter("test", "localhost:0", sinkProvider);
        InsightMicrometerBridge bridge = new InsightMicrometerBridge(registry, reporter);

        TraceSpan span = new TraceSpan();
        span.setSpanKind("CLIENT");
        span.setRemoteService("sca-user");
        span.finish();

        bridge.recordSpan(span, true);

        assertEquals(1.0, registry.get("spring.insight.spans.accepted").counter().count());
        assertTrue(registry.find("spring.insight.span")
                .tag("span.kind", "CLIENT")
                .tag("remote.service", "sca-user")
                .timer()
                .count() >= 1);
    }
}
