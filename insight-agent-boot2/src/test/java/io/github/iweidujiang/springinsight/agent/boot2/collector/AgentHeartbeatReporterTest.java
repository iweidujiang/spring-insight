package io.github.iweidujiang.springinsight.agent.boot2.collector;

import io.github.iweidujiang.springinsight.agent.boot2.AgentBuildInfo;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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

        AgentHeartbeatReporter reporter = new AgentHeartbeatReporter(
                properties, providerOf(sink), providerOf((AsyncSpanReporter) null), true, 60_000);
        reporter.sendImmediately();

        AgentHeartbeatReport report = captured.get();
        assertNotNull(report);
        assertEquals("demo-svc", report.getServiceName());
        assertEquals("localhost:8081", report.getServiceInstance());
        assertNotNull(report.getTimestamp());
        assertEquals(AgentBuildInfo.agentVersion(), report.getAgentVersion());
        assertEquals(AgentBuildInfo.protocol(), report.getProtocol());
        assertNull(report.getQueueDropped());
    }

    @Test
    public void buildReport_fillsServiceFields() {
        InsightBoot2Properties properties = new InsightBoot2Properties();
        properties.setServiceName("order");
        properties.setServiceInstance("i-1");
        AgentHeartbeatReporter reporter = new AgentHeartbeatReporter(
                properties,
                providerOf((InsightBatchSink) null),
                providerOf((AsyncSpanReporter) null),
                false,
                30_000);
        AgentHeartbeatReport report = reporter.buildReport();
        assertEquals("order", report.getServiceName());
        assertEquals("i-1", report.getServiceInstance());
        assertEquals(AgentBuildInfo.agentVersion(), report.getAgentVersion());
        assertEquals(AgentBuildInfo.protocol(), report.getProtocol());
        assertTrue(report.getTimestamp().toEpochMilli() > 0L);
        assertNull(report.getQueueDropped());
    }

    @Test
    public void buildReport_attachesDropStatsWhenEnabled() {
        InsightBoot2Properties properties = new InsightBoot2Properties();
        properties.setServiceName("pressured");
        properties.setServiceInstance("i-2");
        AsyncSpanReporter spanReporter = new AsyncSpanReporter(
                "pressured", providerOf((InsightBatchSink) null), 2);
        spanReporter.start();
        try {
            AgentHeartbeatReporter reporter = new AgentHeartbeatReporter(
                    properties,
                    providerOf((InsightBatchSink) null),
                    providerOf(spanReporter),
                    true,
                    60_000);
            AgentHeartbeatReport report = reporter.buildReport();
            assertEquals(Long.valueOf(0L), report.getQueueDropped());
            assertEquals(Integer.valueOf(0), report.getQueueSize());
            assertEquals(Integer.valueOf(2), report.getQueueCapacity());
        } finally {
            spanReporter.stop();
        }
    }

    private static <T> ObjectProvider<T> providerOf(final T bean) {
        return new ObjectProvider<T>() {
            @Override
            public T getObject() {
                return bean;
            }

            @Override
            public T getObject(Object... args) {
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
                return bean == null ? Stream.<T>empty() : Stream.of(bean);
            }

            @Override
            public Stream<T> orderedStream() {
                return stream();
            }

            @Override
            public Iterator<T> iterator() {
                return stream().iterator();
            }
        };
    }
}
