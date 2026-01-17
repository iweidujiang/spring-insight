package io.github.iweidujiang.springinsight.storage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用指标聚合实体类
 * 对应数据库表: insight_service_metrics
 */
@Data
@TableName("insight_service_metrics")
public class ServiceMetricDO {

    // ========== 主键 ==========
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    // ========== 服务信息 ==========
    @TableField(value = "service_name")
    private String serviceName;

    @TableField(value = "operation_name")
    private String operationName;

    // ========== 指标数据 ==========
    @TableField(value = "qps")
    private Double qps;

    @TableField(value = "avg_latency_ms")
    private Double avgLatencyMs;

    @TableField(value = "p95_latency_ms")
    private Double p95LatencyMs;

    @TableField(value = "p99_latency_ms")
    private Double p99LatencyMs;

    @TableField(value = "error_rate")
    private Double errorRate;

    // ========== 指标类型 ==========
    @TableField(value = "metric_type")
    private String metricType;

    // ========== 时间窗口 ==========
    @TableField(value = "window_time")
    private LocalDateTime windowTime;

    // ========== 数据管理字段 ==========
    @TableField(value = "gmt_create", fill = FieldFill.INSERT)
    private LocalDateTime gmtCreate;
}