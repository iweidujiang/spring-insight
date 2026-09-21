/**
 * TraceSpan：Boot2 兼容线核心追踪数据单元（字段与主线对齐，供 Server 反序列化）。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class TraceSpan {

    private String traceId;
    private String spanId;
    private String parentSpanId;

    private String serviceName;
    private String serviceInstance;
    private String hostIp;
    private Integer hostPort;

    private String operationName;
    private String spanKind;
    private String component;
    private String endpoint;

    private Long startTime;
    private Long endTime;
    private Long durationMs;

    private String statusCode;
    private Boolean success;
    private String errorCode;
    private String errorMessage;

    private String remoteService;
    private String remoteEndpoint;

    private Map<String, String> tags = new HashMap<String, String>();

    @JsonIgnore
    private volatile boolean finished = false;

    @JsonIgnore
    private final Instant createTime = Instant.now();

    /**
     * 创建根 Span。
     */
    public TraceSpan() {
        this.traceId = generateId();
        this.spanId = generateId();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 创建子 Span。
     *
     * @param traceId      与父共享的 TraceId
     * @param parentSpanId 父 SpanId
     */
    public TraceSpan(String traceId, String parentSpanId) {
        if (traceId == null || traceId.trim().isEmpty()) {
            throw new IllegalArgumentException("TraceId 不能为空");
        }
        this.traceId = traceId;
        this.parentSpanId = parentSpanId;
        this.spanId = generateId();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 正常结束 Span。
     */
    public void finish() {
        finish(null, null);
    }

    /**
     * 结束 Span；若传入错误码/信息则标记失败。
     *
     * @param errorCode    错误码，可为 null
     * @param errorMessage 错误信息，可为 null
     */
    public void finish(String errorCode, String errorMessage) {
        if (finished) {
            return;
        }
        this.endTime = System.currentTimeMillis();
        this.durationMs = this.endTime - this.startTime;
        if (errorCode != null || errorMessage != null) {
            this.statusCode = "ERROR";
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.success = Boolean.FALSE;
        } else {
            this.statusCode = "OK";
            this.success = Boolean.TRUE;
        }
        this.finished = true;
    }

    /**
     * 生成简易唯一 ID。
     *
     * @return hex 时间戳拼接串
     */
    public static String generateId() {
        return Long.toHexString(System.currentTimeMillis())
                + Long.toHexString(System.nanoTime() % 1000000L);
    }

    /**
     * 添加标签。
     *
     * @param key   标签键
     * @param value 标签值
     * @return this
     */
    public TraceSpan addTag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }

    /**
     * @return 是否已结束
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * 深拷贝快照，避免异步队列读到热路径后续修改。
     *
     * @param s 源 Span
     * @return 快照副本
     */
    public static TraceSpan snapshot(TraceSpan s) {
        TraceSpan t = new TraceSpan();
        t.setTraceId(s.getTraceId());
        t.setSpanId(s.getSpanId());
        t.setParentSpanId(s.getParentSpanId());
        t.setServiceName(s.getServiceName());
        t.setServiceInstance(s.getServiceInstance());
        t.setHostIp(s.getHostIp());
        t.setHostPort(s.getHostPort());
        t.setOperationName(s.getOperationName());
        t.setSpanKind(s.getSpanKind());
        t.setComponent(s.getComponent());
        t.setEndpoint(s.getEndpoint());
        t.setStartTime(s.getStartTime());
        t.setEndTime(s.getEndTime());
        t.setDurationMs(s.getDurationMs());
        t.setStatusCode(s.getStatusCode());
        t.setSuccess(s.getSuccess());
        t.setErrorCode(s.getErrorCode());
        t.setErrorMessage(s.getErrorMessage());
        t.setRemoteService(s.getRemoteService());
        t.setRemoteEndpoint(s.getRemoteEndpoint());
        if (s.getTags() != null) {
            t.setTags(new HashMap<String, String>(s.getTags()));
        }
        t.finished = s.finished;
        return t;
    }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public void setParentSpanId(String parentSpanId) { this.parentSpanId = parentSpanId; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getServiceInstance() { return serviceInstance; }
    public void setServiceInstance(String serviceInstance) { this.serviceInstance = serviceInstance; }
    public String getHostIp() { return hostIp; }
    public void setHostIp(String hostIp) { this.hostIp = hostIp; }
    public Integer getHostPort() { return hostPort; }
    public void setHostPort(Integer hostPort) { this.hostPort = hostPort; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public String getSpanKind() { return spanKind; }
    public void setSpanKind(String spanKind) { this.spanKind = spanKind; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public Long getStartTime() { return startTime; }
    public void setStartTime(Long startTime) { this.startTime = startTime; }
    public Long getEndTime() { return endTime; }
    public void setEndTime(Long endTime) { this.endTime = endTime; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRemoteService() { return remoteService; }
    public void setRemoteService(String remoteService) { this.remoteService = remoteService; }
    public String getRemoteEndpoint() { return remoteEndpoint; }
    public void setRemoteEndpoint(String remoteEndpoint) { this.remoteEndpoint = remoteEndpoint; }
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    public Instant getCreateTime() { return createTime; }
}
