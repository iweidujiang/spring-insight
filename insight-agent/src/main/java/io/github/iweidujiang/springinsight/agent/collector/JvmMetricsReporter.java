package io.github.iweidujiang.springinsight.agent.collector;

import io.github.iweidujiang.springinsight.agent.model.JvmMetric;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * JVM 指标报告器
 * 定期采集JVM指标并上报到Collector服务
 */
@Slf4j
public class JvmMetricsReporter {

    private static final int DEFAULT_REPORT_INTERVAL_MS = 30000; // 默认30秒上报一次

    private final JvmMetricsCollector metricsCollector;
    private final AsyncSpanReporter spanReporter;
    private final int reportIntervalMs;
    private final String serviceName;

    /**
     * 构造函数
     */
    public JvmMetricsReporter(JvmMetricsCollector metricsCollector, AsyncSpanReporter spanReporter) {
        this(metricsCollector, spanReporter, DEFAULT_REPORT_INTERVAL_MS);
    }

    /**
     * 构造函数
     */
    public JvmMetricsReporter(JvmMetricsCollector metricsCollector, AsyncSpanReporter spanReporter, int reportIntervalMs) {
        this.metricsCollector = metricsCollector;
        this.spanReporter = spanReporter;
        this.reportIntervalMs = reportIntervalMs;
        this.serviceName = metricsCollector.getServiceName();

        log.info("[JVM指标报告器] 初始化完成，上报间隔: {}ms", reportIntervalMs);
    }

    /**
     * 开始定期上报JVM指标
     * 注意：需要在Spring容器中启用@EnableScheduling
     */
    @Scheduled(fixedDelayString = "${spring.insight.jvm-metrics.report-interval:30000}")
    public void reportJvmMetrics() {
        try {
            log.debug("[JVM指标报告器] 开始上报JVM指标");

            // 采集JVM指标
            JvmMetric metric = metricsCollector.collectMetrics();

            // 上报指标
            boolean success = spanReporter.report(metric);

            if (success) {
                log.debug("[JVM指标报告器] JVM指标上报成功: service={}, heapMemoryUsed={}MB, gcCount={}",
                        serviceName,
                        metric.getHeapMemoryUsed() / 1024 / 1024,
                        metric.getGcCount());
            } else {
                log.warn("[JVM指标报告器] JVM指标上报失败（可能队列已满）: service={}", serviceName);
            }

        } catch (Exception e) {
            log.error("[JVM指标报告器] 上报JVM指标失败: service={}, error={}", serviceName, e.getMessage(), e);
        }
    }

    /**
     * 立即上报一次JVM指标
     */
    public void reportImmediately() {
        reportJvmMetrics();
    }
}