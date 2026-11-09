package io.github.iweidujiang.springinsight.agent.boot2.collector;

import io.github.iweidujiang.springinsight.agent.boot2.model.JvmMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定时采集 JVM 指标并写入异步上报队列。
 *
 * @author 苏渡苇
 * @since 2026-11-09
 */
public class JvmMetricsReporter {

    private static final Logger log = LoggerFactory.getLogger(JvmMetricsReporter.class);

    private final JvmMetricsCollector metricsCollector;
    private final AsyncSpanReporter spanReporter;
    private final String serviceName;

    /**
     * @param metricsCollector 采集器
     * @param spanReporter     异步上报器
     */
    public JvmMetricsReporter(JvmMetricsCollector metricsCollector, AsyncSpanReporter spanReporter) {
        this.metricsCollector = metricsCollector;
        this.spanReporter = spanReporter;
        this.serviceName = metricsCollector.getServiceName();
        log.info("[JVM指标报告器-Boot2] 初始化完成");
    }

    /**
     * 按配置间隔上报；需启用调度。
     */
    @Scheduled(fixedDelayString = "${spring.insight.jvm-metrics.report-interval:30000}")
    public void reportJvmMetrics() {
        try {
            JvmMetric metric = metricsCollector.collectMetrics();
            boolean success = spanReporter.report(metric);
            if (success) {
                log.debug("[JVM指标报告器-Boot2] 上报成功: service={}, heapUsedMB={}, gcCount={}",
                        serviceName,
                        Long.valueOf(metric.getHeapMemoryUsed() / 1024L / 1024L),
                        Long.valueOf(metric.getGcCount()));
            } else {
                log.warn("[JVM指标报告器-Boot2] 上报失败（可能队列已满）: service={}", serviceName);
            }
        } catch (Exception e) {
            log.error("[JVM指标报告器-Boot2] 上报异常: service={}, error={}", serviceName, e.getMessage());
        }
    }
}
