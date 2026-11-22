package io.github.iweidujiang.springinsight.agent.collector;

import io.github.iweidujiang.springinsight.agent.AgentBuildInfo;
import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.model.AgentHeartbeatReport;
import io.github.iweidujiang.springinsight.agent.sink.InsightBatchSink;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 心跳报告器单测。
 *
 * @author 苏渡苇
 * @since 2026-11-20
 */
class AgentHeartbeatReporterTest {

    @Test
    void sendHeartbeat_forwardsReportToSink() {
        InsightProperties properties = new InsightProperties();
        properties.setServiceName("demo-svc");
        properties.setServiceInstance("localhost:8081");

        AtomicReference<AgentHeartbeatReport> captured = new AtomicReference<>();
        InsightBatchSink sink = new InsightBatchSink() {
            @Override
            public void acceptTraceSpans(java.util.List<io.github.iweidujiang.springinsight.agent.model.TraceSpan> spans) {
            }

            @Override
            public void acceptJvmMetrics(java.util.List<io.github.iweidujiang.springinsight.agent.model.JvmMetric> metrics) {
            }

            @Override
            public void acceptHeartbeat(AgentHeartbeatReport heartbeat) {
                captured.set(heartbeat);
            }
        };

        AgentHeartbeatReporter reporter = new AgentHeartbeatReporter(
                properties, providerOf(sink), providerOf((AsyncSpanReporter) null), true, 60_000);
        reporter.sendImmediately();

        AgentHeartbeatReport report = captured.get();
        assertNotNull(report);
        assertEquals("demo-svc", report.getServiceName());
        assertEquals("localhost:8081", report.getServiceInstance());
        assertNotNull(report.getTimestamp());
        assertNull(report.getQueueDropped());
    }

    @Test
    void buildReport_fillsServiceFields() {
        InsightProperties properties = new InsightProperties();
        properties.setServiceName("order");
        properties.setServiceInstance("i-1");
        AgentHeartbeatReporter reporter = new AgentHeartbeatReporter(
                properties, providerOf((InsightBatchSink) null), providerOf((AsyncSpanReporter) null), false, 30_000);
        AgentHeartbeatReport report = reporter.buildReport();
        assertEquals("order", report.getServiceName());
        assertEquals("i-1", report.getServiceInstance());
        assertEquals(AgentBuildInfo.agentVersion(), report.getAgentVersion());
        assertEquals(AgentBuildInfo.protocol(), report.getProtocol());
        assertTrue(report.getTimestamp().toEpochMilli() > 0L);
        assertNull(report.getQueueDropped());
    }

    @Test
    void buildReport_attachesDropStatsWhenEnabled() {
        InsightProperties properties = new InsightProperties();
        properties.setServiceName("pressured");
        properties.setServiceInstance("i-2");
        AsyncSpanReporter spanReporter = new AsyncSpanReporter(
                "pressured", "i-2", providerOf((InsightBatchSink) null), 2);
        spanReporter.start();
        try {
            AgentHeartbeatReporter reporter = new AgentHeartbeatReporter(
                    properties, providerOf((InsightBatchSink) null), providerOf(spanReporter), true, 60_000);
            AgentHeartbeatReport report = reporter.buildReport();
            assertEquals(0L, report.getQueueDropped());
            assertEquals(0, report.getQueueSize());
            assertEquals(2, report.getQueueCapacity());
        } finally {
            spanReporter.stop();
        }
    }

    private static <T> ObjectProvider<T> providerOf(T bean) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return bean;
            }

            @Override
            public T getIfAvailable() {
                return bean;
            }

            @Override
            public T getIfUnique() {
                return bean;
            }

            @Override
            public Stream<T> stream() {
                return bean == null ? Stream.empty() : Stream.of(bean);
            }
        };
    }
}
