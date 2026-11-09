package io.github.iweidujiang.springinsight.collector.model;

import io.github.iweidujiang.springinsight.agent.model.JvmMetric;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Agent JVM 指标批量上报请求。
 *
 * @author 苏渡苇
 * @since 2026-11-09
 */
@Data
public class JvmMetricBatchRequest {

    /** 服务名称 */
    @NotBlank(message = "服务名称不能为空")
    private String serviceName;

    /** 服务实例标识 */
    @NotBlank(message = "服务实例标识不能为空")
    private String serviceInstance;

    /** 批次 ID */
    private String batchId;

    /** 上报时间 */
    private Instant reportTime = Instant.now();

    /** JVM 指标点 */
    @NotNull(message = "JVM 指标列表不能为空")
    private List<JvmMetric> metrics;

    /**
     * @return 是否具备可落库的指标点
     */
    public boolean hasMetrics() {
        return metrics != null && !metrics.isEmpty();
    }

    /**
     * @return 日志摘要
     */
    public String getSummary() {
        return String.format("service=%s, instance=%s, metricCount=%d, batchId=%s",
                serviceName, serviceInstance, metrics != null ? metrics.size() : 0, batchId);
    }
}
