package io.github.iweidujiang.springinsight.agent.boot2.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JVM 指标批量上报包，字段与 Server {@code JvmMetricBatchRequest} 对齐。
 *
 * @author 苏渡苇
 * @since 2026-11-09
 */
public class JvmMetricBatchReport {

    private String serviceName;
    private String serviceInstance;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant reportTime = Instant.now();

    private String batchId;
    private List<JvmMetric> metrics = new ArrayList<JvmMetric>();

    /**
     * 批量加入指标点。
     *
     * @param points 指标列表；空或 null 时忽略
     */
    public void addAllMetrics(List<JvmMetric> points) {
        if (points != null && !points.isEmpty()) {
            this.metrics.addAll(points);
        }
    }

    /**
     * @return 本批指标条数
     */
    public int getMetricCount() {
        return metrics != null ? metrics.size() : 0;
    }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getServiceInstance() { return serviceInstance; }
    public void setServiceInstance(String serviceInstance) { this.serviceInstance = serviceInstance; }
    public Instant getReportTime() { return reportTime; }
    public void setReportTime(Instant reportTime) { this.reportTime = reportTime; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public List<JvmMetric> getMetrics() { return metrics; }
    public void setMetrics(List<JvmMetric> metrics) { this.metrics = metrics; }
}
