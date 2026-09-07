/*
 * Copyright (c) 2026, 苏渡苇. All rights reserved.
 *
 * TraceSpan：Boot2 兼容线核心追踪数据单元（字段与主线对齐，供 Server 反序列化）。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Data
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

    public TraceSpan() {
        this.traceId = generateId();
        this.spanId = generateId();
        this.startTime = System.currentTimeMillis();
    }

    public TraceSpan(String traceId, String parentSpanId) {
        if (traceId == null || traceId.trim().isEmpty()) {
            throw new IllegalArgumentException("TraceId 不能为空");
        }
        this.traceId = traceId;
        this.parentSpanId = parentSpanId;
        this.spanId = generateId();
        this.startTime = System.currentTimeMillis();
    }

    public void finish() {
        finish(null, null);
    }

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

    public static String generateId() {
        return Long.toHexString(System.currentTimeMillis())
                + Long.toHexString(System.nanoTime() % 1000000L);
    }

    public TraceSpan addTag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }

    public boolean isFinished() {
        return finished;
    }

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
}
