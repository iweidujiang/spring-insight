package io.github.iweidujiang.springinsight.agent.boot2.collector;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.model.AgentHeartbeatReport;
import io.github.iweidujiang.springinsight.agent.boot2.model.JvmMetric;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import io.github.iweidujiang.springinsight.agent.boot2.sink.InsightBatchSink;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boot2 Agent 心跳报告器单测。
 *
 * @author 苏渡苇
 * @since 2026-11-20
 */
public class AgentHeartbeatReporterTest {

    @Test
    public void sendHeartbeat_forwardsReportToSink() {
        InsightBoot2Properties properties = new InsightBoot2Properties();
        properties.setServiceName("demo-svc");
        properties.setServiceInstance("localhost:8081");

        final AtomicReference<AgentHeartbeatReport> captured = new AtomicReference<AgentHeartbeatReport>();
        InsightBatchSink sink = new InsightBatchSink() {
            @Override
            public void acceptTraceSpans(List<TraceSpan> spans) {
            }

            @Override
            public void acceptJvmMetrics(List<JvmMetric> metrics) {
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
    public void buildReport_fillsServiceFields() {
        InsightBoot2Properties properties = new InsightBoot2Properties();
        properties.setServiceName("order");
        properties.setServiceInstance("i-1");
        AgentHeartbeatReporter reporter = new AgentHeartbeatReporter(properties, providerOf(null), 30_000);
        AgentHeartbeatReport report = reporter.buildReport();
        assertEquals("order", report.getServiceName());
        assertEquals("i-1", report.getServiceInstance());
        assertTrue(report.getTimestamp().toEpochMilli() > 0L);
    }

    private static ObjectProvider<InsightBatchSink> providerOf(final InsightBatchSink sink) {
        return new ObjectProvider<InsightBatchSink>() {
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
                return sink == null ? Stream.<InsightBatchSink>empty() : Stream.of(sink);
            }

            @Override
            public Stream<InsightBatchSink> orderedStream() {
                return stream();
            }

            @Override
            public Iterator<InsightBatchSink> iterator() {
                return stream().iterator();
            }
        };
    }
}
