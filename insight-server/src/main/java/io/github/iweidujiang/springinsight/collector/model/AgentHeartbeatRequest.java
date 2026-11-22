package io.github.iweidujiang.springinsight.collector.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;

/**
 * Agent 存活心跳上报请求。
 *
 * @author 苏渡苇
 * @since 2026-11-20
 */
@Data
public class AgentHeartbeatRequest {

    /** 服务名称 */
    @NotBlank(message = "服务名称不能为空")
    private String serviceName;

    /** 服务实例标识 */
    private String serviceInstance;

    /** Agent / Starter 版本（可空） */
    private String agentVersion;

    /** 上报协议号（可空） */
    private String protocol;

    /** 心跳时刻；缺省时服务端用接收时间 */
    private Instant timestamp;

    /**
     * @return 日志摘要
     */
    public String getSummary() {
        return String.format("service=%s, instance=%s, at=%s",
                serviceName, serviceInstance, timestamp);
    }
}
