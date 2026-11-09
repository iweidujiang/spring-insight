package io.github.iweidujiang.springinsight.agent.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JVM 指标批量上报包，字段与服务端 {@code JvmMetricBatchRequest} 对齐。
 *
 * @author 苏渡苇
 * @since 2026-11-09
 */
@Data
public class JvmMetricBatchReport {

    /** 上报的服务名称 */
    private String serviceName;

    /** 上报的服务实例 */
    private String serviceInstance;

    /** 上报时间 */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant reportTime = Instant.now();

    /** 批次 ID */
    private String batchId;

    /** JVM 指标列表 */
    private List<JvmMetric> metrics = new ArrayList<>();

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
}
