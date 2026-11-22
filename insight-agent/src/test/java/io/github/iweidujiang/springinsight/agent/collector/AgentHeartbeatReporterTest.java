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

        AgentHeartbeatReporter reporter = new AgentHeartbeatReporter(properties, providerOf(sink), 60_000);
        reporter.sendImmediately();

        AgentHeartbeatReport report = captured.get();
        assertNotNull(report);
        assertEquals("demo-svc", report.getServiceName());
        assertEquals("localhost:8081", report.getServiceInstance());
        assertNotNull(report.getTimestamp());
    }

    @Test
    void buildReport_fillsServiceFields() {
        InsightProperties properties = new InsightProperties();
        properties.setServiceName("order");
        properties.setServiceInstance("i-1");
        AgentHeartbeatReporter reporter = new AgentHeartbeatReporter(properties, providerOf(null), 30_000);
        AgentHeartbeatReport report = reporter.buildReport();
        assertEquals("order", report.getServiceName());
        assertEquals("i-1", report.getServiceInstance());
        assertEquals(AgentBuildInfo.agentVersion(), report.getAgentVersion());
        assertEquals(AgentBuildInfo.protocol(), report.getProtocol());
        assertTrue(report.getTimestamp().toEpochMilli() > 0L);
    }

    private static ObjectProvider<InsightBatchSink> providerOf(InsightBatchSink sink) {
        return new ObjectProvider<>() {
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
                return sink == null ? Stream.empty() : Stream.of(sink);
            }
        };
    }
}
