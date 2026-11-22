package io.github.iweidujiang.springinsight.agent.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.Instant;

/**
 * Agent 存活心跳上报体，对应 Server {@code POST /api/v1/agent/heartbeat}。
 *
 * @author 苏渡苇
 * @since 2026-11-20
 */
@Data
public class AgentHeartbeatReport {

    /** 服务名 */
    private String serviceName;

    /** 服务实例 */
    private String serviceInstance;

    /** Agent / Starter 版本 */
    private String agentVersion;

    /** 上报协议号 */
    private String protocol;

    /**
     * 上报队列因满而丢弃的累计次数（可选；未开启 drop-stats 时为 null）。
     */
    private Long queueDropped;

    /** 当前队列深度（可选） */
    private Integer queueSize;

    /** 队列容量（可选） */
    private Integer queueCapacity;

    /** 心跳时刻（UTC） */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp = Instant.now();
}
