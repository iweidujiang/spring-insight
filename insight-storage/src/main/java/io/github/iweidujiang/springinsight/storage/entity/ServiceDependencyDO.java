package io.github.iweidujiang.springinsight.storage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务依赖关系实体类
 * 对应数据库表: insight_service_dependencies
 */
@Data
@TableName("insight_service_dependencies")
public class ServiceDependencyDO {

    // ========== 主键 ==========
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    // ========== 服务依赖信息 ==========
    @TableField(value = "source_service")
    private String sourceService;

    @TableField(value = "target_service")
    private String targetService;

    @TableField(value = "total_calls")
    private Long totalCalls;

    @TableField(value = "success_calls")
    private Long successCalls;

    @TableField(value = "error_calls")
    private Long errorCalls;

    @TableField(value = "total_duration_ms")
    private Long totalDurationMs;

    @TableField(value = "avg_duration_ms")
    private Double avgDurationMs;

    @TableField(value = "max_duration_ms")
    private Long maxDurationMs;

    @TableField(value = "min_duration_ms")
    private Long minDurationMs;

    // ========== 统计窗口信息 ==========
    @TableField(value = "stat_window")
    private String statWindow;

    @TableField(value = "window_start_time")
    private Long windowStartTime;

    @TableField(value = "window_end_time")
    private Long windowEndTime;

    @TableField(value = "cal_version")
    private Integer calVersion;

    // ========== 数据管理字段 ==========
    @TableField(value = "gmt_create", fill = FieldFill.INSERT)
    private LocalDateTime gmtCreate;

    @TableField(value = "gmt_modified", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime gmtModified;
}