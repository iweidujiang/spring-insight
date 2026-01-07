package io.github.iweidujiang.springinsight.agent.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 核心追踪数据单元
 *
 * @author <a href="https://github.com/iweidujiang">...</a>
 * @since 2026/1/7
 */
@Data
public class Span {
    // ========== 追踪标识 ==========
    /** 全局唯一的追踪ID，一个请求链路上的所有Span共享此ID */
    private String traceId;
    /** 当前Span的唯一标识 */
    private String spanId;
    /** 父Span的ID，用于构建调用树，根Span此值为null */
    private String parentSpanId;

    // ========== 应用信息 ==========
    /** 服务名称，如 `user-service` */
    private String serviceName;
    /** 服务实例标识，通常是主机名或IP+端口 */
    private String serviceInstance;
    /** 发生Span的服务器IP */
    private String host;
    /** 发生Span的服务器端口 */
    private Integer port;

    // ========== 操作信息 ==========
    /** 操作名称，如 `GET /api/users` */
    private String operationName;
    /** Span类型: HTTP, DB, REDIS, RPC, INTERNAL */
    private String spanType;
    /** 具体的端点或方法，如 `com.example.UserController.getUser` */
    private String endpoint;

    // ========== 时间信息 ==========
    /** 开始时间戳 (毫秒) */
    private Long startTime;
    /** 结束时间戳 (毫秒) */
    private Long endTime;
    /** 持续时间 (毫秒)，由 endTime - startTime 计算得出 */
    private Long durationMs;

    // ========== 状态信息 ==========
    /** 是否成功 */
    private Boolean success;
    /** 错误码 */
    private String errorCode;
    /** 错误信息 */
    private String errorMessage;

    // ========== 依赖/目标信息 (用于拓扑分析) ==========
    /** 调用的目标服务（跨服务调用时填写） */
    private String targetService;
    /** 调用的目标端点 */
    private String targetEndpoint;

    // ========== 标签 (用于详细分类和筛选) ==========
    private Map<String, String> tags = new HashMap<>();

    /**
     * 创建一个HTTP类型的Span
     */
    public static Span createHttpSpan(String traceId, String serviceName,
                                      String operationName, Long startTime,
                                      Long durationMs, Boolean success) {
        Span span = new Span();
        span.setTraceId(traceId);
        span.setSpanId(generateId());
        span.setServiceName(serviceName);
        span.setOperationName(operationName);
        span.setSpanType("HTTP");
        span.setStartTime(startTime);
        span.setEndTime(startTime + durationMs);
        span.setDurationMs(durationMs);
        span.setSuccess(success);
        return span;
    }

    /**
     * 生成一个唯一ID
     */
    public static String generateId() {
        return Long.toHexString(System.currentTimeMillis()) +
                Long.toHexString(System.nanoTime() % 1000000);
    }

    /**
     * 添加标签
     */
    public Span addTag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }
}
