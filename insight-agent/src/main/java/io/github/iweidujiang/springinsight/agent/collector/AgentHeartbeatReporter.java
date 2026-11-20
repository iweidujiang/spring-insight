package io.github.iweidujiang.springinsight.agent.collector;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.model.AgentHeartbeatReport;
import io.github.iweidujiang.springinsight.agent.sink.InsightBatchSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

/**
 * 定期向监测中心上报存活心跳（不经 Span 队列，避免与业务上报抢容量）。
 *
 * @author 苏渡苇
 * @since 2026-11-20
 */
@Slf4j
public class AgentHeartbeatReporter {

    private final InsightProperties properties;
    private final ObjectProvider<InsightBatchSink> batchSinkProvider;
    private final int intervalMs;

    /**
     * @param properties         Insight 主配置
     * @param batchSinkProvider  批量/心跳出口（有 server-url 时为 HTTP Sink）
     * @param intervalMs         配置的间隔，仅用于启动日志
     */
    public AgentHeartbeatReporter(InsightProperties properties,
                                  ObjectProvider<InsightBatchSink> batchSinkProvider,
                                  int intervalMs) {
        this.properties = properties;
        this.batchSinkProvider = batchSinkProvider;
        this.intervalMs = intervalMs > 0 ? intervalMs : 60_000;
        log.info("[心跳] 已启用，间隔={}ms，service={}", this.intervalMs, properties.getServiceName());
    }

    /**
     * 定时发送心跳；失败只打日志，不抛到调度线程外。
     */
    @Scheduled(fixedDelayString = "${spring.insight.heartbeat.interval-ms:60000}")
    public void sendHeartbeat() {
        try {
            InsightBatchSink sink = batchSinkProvider.getIfAvailable();
            if (sink == null) {
                log.debug("[心跳] 无 InsightBatchSink（通常未配 server-url），跳过");
                return;
            }
            AgentHeartbeatReport report = buildReport();
            log.debug("[心跳] 上报: service={}, instance={}, at={}",
                    report.getServiceName(), report.getServiceInstance(), report.getTimestamp());
            sink.acceptHeartbeat(report);
        } catch (Exception e) {
            log.warn("[心跳] 上报异常: service={}, error={}", properties.getServiceName(), e.getMessage());
        }
    }

    /**
     * 立即发一次（便于测试或启动后主动探活）。
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
        report.setTimestamp(Instant.now());
        return report;
    }

    private String resolveServiceInstance() {
        String si = properties.getServiceInstance();
        if (si != null && !si.isBlank()) {
            return si;
        }
        String port = System.getProperty("server.port", "8080");
        if ("0".equals(port)) {
            port = "8080";
        }
        return "localhost:" + port;
    }
}
