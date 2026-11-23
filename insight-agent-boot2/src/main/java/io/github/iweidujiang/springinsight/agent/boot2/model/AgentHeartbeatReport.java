package io.github.iweidujiang.springinsight.agent.boot2.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * Agent 存活心跳上报体，对应 Server {@code POST /api/v1/agent/heartbeat}。
 *
 * @author 苏渡苇
 * @since 2026-11-20
 */
public class AgentHeartbeatReport {

    private String serviceName;
    private String serviceInstance;
    private String agentVersion;
    private String protocol;
    /** 上报队列因满而丢弃的累计次数（可选；未开启 drop-stats 时为 null） */
    private Long queueDropped;
    private Integer queueSize;
    private Integer queueCapacity;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp = Instant.now();

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceInstance() {
        return serviceInstance;
    }

    public void setServiceInstance(String serviceInstance) {
        this.serviceInstance = serviceInstance;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Long getQueueDropped() {
        return queueDropped;
    }

    public void setQueueDropped(Long queueDropped) {
        this.queueDropped = queueDropped;
    }

    public Integer getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(Integer queueSize) {
        this.queueSize = queueSize;
    }

    public Integer getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(Integer queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
