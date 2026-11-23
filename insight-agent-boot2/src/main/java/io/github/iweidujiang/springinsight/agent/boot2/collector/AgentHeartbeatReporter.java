package io.github.iweidujiang.springinsight.agent.boot2.collector;

import io.github.iweidujiang.springinsight.agent.boot2.AgentBuildInfo;
import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.model.AgentHeartbeatReport;
import io.github.iweidujiang.springinsight.agent.boot2.sink.InsightBatchSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

/**
 * 定期向监测中心上报存活心跳（不经 Span 队列，避免与业务上报抢容量）。
 *
 * @author 苏渡苇
 * @since 2026-11-20
 */
public class AgentHeartbeatReporter {

    private static final Logger log = LoggerFactory.getLogger(AgentHeartbeatReporter.class);

    private final InsightBoot2Properties properties;
    private final ObjectProvider<InsightBatchSink> batchSinkProvider;
    private final ObjectProvider<AsyncSpanReporter> spanReporterProvider;
    private final boolean dropStatsEnabled;
    private final int intervalMs;

    /**
     * @param properties           Insight 主配置
     * @param batchSinkProvider    批量/心跳出口（有 server-url 时为 HTTP Sink）
     * @param spanReporterProvider 异步上报器（取队列丢弃统计）
     * @param dropStatsEnabled     是否附带丢弃 / 队列深度
     * @param intervalMs           配置的间隔，仅用于启动日志
     */
    public AgentHeartbeatReporter(InsightBoot2Properties properties,
                                  ObjectProvider<InsightBatchSink> batchSinkProvider,
                                  ObjectProvider<AsyncSpanReporter> spanReporterProvider,
                                  boolean dropStatsEnabled,
                                  int intervalMs) {
        this.properties = properties;
        this.batchSinkProvider = batchSinkProvider;
        this.spanReporterProvider = spanReporterProvider;
        this.dropStatsEnabled = dropStatsEnabled;
        this.intervalMs = intervalMs > 0 ? intervalMs : 60_000;
        log.info("[心跳-Boot2] 已启用，间隔={}ms，dropStats={}，service={}",
                Integer.valueOf(this.intervalMs),
                Boolean.valueOf(dropStatsEnabled),
                properties.getServiceName());
    }

    /**
     * 定时发送心跳；失败只打日志，不抛到调度线程外。
     */
    @Scheduled(fixedDelayString = "${spring.insight.heartbeat.interval-ms:60000}")
    public void sendHeartbeat() {
        try {
            InsightBatchSink sink = batchSinkProvider.getIfAvailable();
            if (sink == null) {
                log.debug("[心跳-Boot2] 无 InsightBatchSink（通常未配 server-url），跳过");
                return;
            }
            AgentHeartbeatReport report = buildReport();
            log.debug("[心跳-Boot2] 上报: service={}, instance={}, dropped={}, queue={}/{}, at={}",
                    report.getServiceName(),
                    report.getServiceInstance(),
                    report.getQueueDropped(),
                    report.getQueueSize(),
                    report.getQueueCapacity(),
                    report.getTimestamp());
            sink.acceptHeartbeat(report);
        } catch (Exception e) {
            log.warn("[心跳-Boot2] 上报异常: service={}, error={}",
                    properties.getServiceName(), e.getMessage());
        }
    }

    /**
     * 立即发一次（便于测试）。
     */
    public void sendImmediately() {
        sendHeartbeat();
    }

    /**
     * 组装当前心跳包。
     *
     * @return 非空上报体
     */
    AgentHeartbeatReport buildReport() {
        AgentHeartbeatReport report = new AgentHeartbeatReport();
        report.setServiceName(properties.getServiceName());
        report.setServiceInstance(resolveServiceInstance());
        report.setAgentVersion(AgentBuildInfo.agentVersion());
        report.setProtocol(AgentBuildInfo.protocol());
        report.setTimestamp(Instant.now());
        if (dropStatsEnabled) {
            AsyncSpanReporter reporter = spanReporterProvider != null
                    ? spanReporterProvider.getIfAvailable()
                    : null;
            if (reporter != null) {
                report.setQueueDropped(Long.valueOf(reporter.getDroppedTotal()));
                report.setQueueSize(Integer.valueOf(reporter.getQueueSize()));
                report.setQueueCapacity(Integer.valueOf(reporter.getQueueCapacity()));
            }
        }
        return report;
    }

    private String resolveServiceInstance() {
        String si = properties.getServiceInstance();
        if (si != null && !si.trim().isEmpty()) {
            return si;
        }
        String port = System.getProperty("server.port", "8080");
        if ("0".equals(port)) {
            port = "8080";
        }
        return "localhost:" + port;
    }
}
