package io.github.iweidujiang.springinsight.storage.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * 链路追踪数据实体类 (Data Object)
 * 对应数据库表: insight_trace_spans
 */
@Slf4j
@Data
@TableName("insight_trace_spans")
public class TraceSpanDO {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ========== 主键 ==========
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    // ========== 追踪标识 ==========
    @TableField(value = "trace_id")
    private String traceId;

    @TableField(value = "span_id")
    private String spanId;

    @TableField(value = "parent_span_id")
    private String parentSpanId;

    // ========== 应用与实例信息 ==========
    @TableField(value = "service_name")
    private String serviceName;

    @TableField(value = "service_instance")
    private String serviceInstance;

    @TableField(value = "host_ip")
    private String hostIp;

    @TableField(value = "host_port")
    private Integer hostPort;

    // ========== 操作信息 ==========
    @TableField(value = "operation_name")
    private String operationName;

    @TableField(value = "span_kind")
    private String spanKind;

    @TableField(value = "component")
    private String component;

    @TableField(value = "endpoint")
    private String endpoint;

    // ========== 时间信息 ==========
    @TableField(value = "start_time")
    private Long startTime;

    @TableField(value = "end_time")
    private Long endTime;

    @TableField(value = "duration_ms")
    private Long durationMs;

    // ========== 状态信息 ==========
    @TableField(value = "status_code")
    private String statusCode;

    @TableField(value = "error_code")
    private String errorCode;

    @TableField(value = "error_message")
    private String errorMessage;

    // ========== 链路拓扑信息 ==========
    @TableField(value = "remote_service")
    private String remoteService;

    @TableField(value = "remote_endpoint")
    private String remoteEndpoint;

    // ========== 标签与扩展信息 ==========
    @TableField(value = "tags_json")
    private String tagsJson;

    // ========== 数据管理字段 ==========
    @TableField(value = "gmt_create", fill = FieldFill.INSERT)
    private LocalDateTime gmtCreate;

    @TableField(value = "gmt_modified", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime gmtModified;

    // ========== 转换方法 ==========

    /**
     * 将业务模型 TraceSpan 转换为数据库实体
     */
    public static TraceSpanDO fromModel(TraceSpan span) {
        TraceSpanDO entity = new TraceSpanDO();

        // 追踪标识
        entity.setTraceId(span.getTraceId());
        entity.setSpanId(span.getSpanId());
        entity.setParentSpanId(span.getParentSpanId());

        // 应用与实例信息
        entity.setServiceName(span.getServiceName());
        entity.setServiceInstance(span.getServiceInstance());
        entity.setHostIp(span.getHostIp());
        entity.setHostPort(span.getHostPort());

        // 操作信息
        entity.setOperationName(span.getOperationName());
        entity.setSpanKind(span.getSpanKind());
        entity.setComponent(span.getComponent());
        entity.setEndpoint(span.getEndpoint());

        // 时间信息
        entity.setStartTime(span.getStartTime());
        entity.setEndTime(span.getEndTime());
        entity.setDurationMs(span.getDurationMs());

        // 状态信息
        entity.setStatusCode(span.getStatusCode());
        entity.setErrorCode(span.getErrorCode());
        entity.setErrorMessage(span.getErrorMessage());

        // 链路拓扑信息
        entity.setRemoteService(span.getRemoteService());
        entity.setRemoteEndpoint(span.getRemoteEndpoint());

        // 标签信息转换为JSON
        if (span.getTags() != null && !span.getTags().isEmpty()) {
            try {
                entity.setTagsJson(OBJECT_MAPPER.writeValueAsString(span.getTags()));
            } catch (JsonProcessingException e) {
                log.error("Failed to convert tags to JSON for span: {}", span.getSpanId(), e);
                entity.setTagsJson("{}");
            }
        }

        return entity;
    }

    /**
     * 将数据库实体转换为业务模型
     */
    public TraceSpan toModel() {
        TraceSpan span = new TraceSpan();

        // 追踪标识
        span.setTraceId(this.traceId);
        span.setSpanId(this.spanId);
        span.setParentSpanId(this.parentSpanId);

        // 应用与实例信息
        span.setServiceName(this.serviceName);
        span.setServiceInstance(this.serviceInstance);
        span.setHostIp(this.hostIp);
        span.setHostPort(this.hostPort);

        // 操作信息
        span.setOperationName(this.operationName);
        span.setSpanKind(this.spanKind);
        span.setComponent(this.component);
        span.setEndpoint(this.endpoint);

        // 时间信息
        span.setStartTime(this.startTime);
        span.setEndTime(this.endTime);
        span.setDurationMs(this.durationMs);

        // 状态信息
        span.setStatusCode(this.statusCode);
        span.setErrorCode(this.errorCode);
        span.setErrorMessage(this.errorMessage);

        // 链路拓扑信息
        span.setRemoteService(this.remoteService);
        span.setRemoteEndpoint(this.remoteEndpoint);

        // 解析JSON标签
        if (StringUtils.hasText(this.tagsJson)) {
            try {
                Map<String, String> tags = OBJECT_MAPPER.readValue(
                        this.tagsJson,
                        new TypeReference<Map<String, String>>() {}
                );
                span.setTags(tags);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse tags JSON for span: {}", this.spanId, e);
                span.setTags(new HashMap<>());
            }
        } else {
            span.setTags(new HashMap<>());
        }

        return span;
    }

    /**
     * 获取开始时间的 Instant 对象
     */
    public Instant getStartInstant() {
        return Instant.ofEpochMilli(startTime);
    }

    /**
     * 获取结束时间的 Instant 对象
     */
    public Instant getEndInstant() {
        return endTime != null ? Instant.ofEpochMilli(endTime) : null;
    }

    /**
     * 获取开始时间的 LocalDateTime
     */
    public LocalDateTime getStartLocalDateTime() {
        return LocalDateTime.ofInstant(getStartInstant(), ZoneId.systemDefault());
    }
}