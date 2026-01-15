package io.github.iweidujiang.springinsight.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 核心追踪数据单元
 *
 * @author <a href="https://github.com/iweidujiang">...</a>
 * @since 2026/1/7
 */
@Slf4j
@Data
public class TraceSpan {
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
    private String hostIp;
    /** 发生Span的服务器端口 */
    private Integer hostPort;

    // ========== 操作信息 ==========
    /** 操作名称，如 `GET /api/users` */
    private String operationName;
    /** Span类型: HTTP, DB, REDIS, RPC, INTERNAL */
    private String spanKind;
    private String component;
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
    private String statusCode;
    /** 是否成功 */
    private Boolean success;
    /** 错误码 */
    private String errorCode;
    /** 错误信息 */
    private String errorMessage;

    // ========== 依赖/目标信息 (用于拓扑分析) ==========
    /** 调用的目标服务（跨服务调用时填写） */
    private String remoteService;
    /** 调用的目标端点 */
    private String remoteEndpoint;

    // ========== 标签 (用于详细分类和筛选) ==========
    private Map<String, String> tags = new HashMap<>();

    // ========== 内部状态（不序列化） ==========
    /** 标记是否已结束 */
    @JsonIgnore
    private volatile boolean finished = false;

    /** 创建时间（用于内部管理） */
    @JsonIgnore
    private final Instant createTime = Instant.now();

    /**
     * 创建一个新的 TraceSpan（根Span）
     */
    public TraceSpan() {
        this.traceId = generateId();
        this.spanId = generateId();
        this.startTime = System.currentTimeMillis();
        log.debug("创建一个新的 TraceSpan: traceId={}, spanId={}", traceId, spanId);
    }

    /**
     * 创建一个子 Span
     */
    public TraceSpan(String traceId, String parentSpanId) {
        if (traceId == null || traceId.trim().isEmpty()) {
            throw new IllegalArgumentException("TraceId 不能为空");
        }

        this.traceId = traceId;
        this.parentSpanId = parentSpanId;
        this.spanId = generateId();
        this.startTime = System.currentTimeMillis();

        log.debug("创建子 span: traceId={}, parentSpanId={}, spanId={}",
                traceId, parentSpanId, spanId);
    }

    // ========== 业务方法 ==========

    /**
     * 结束当前 Span
     */
    public void finish() {
        finish(null, null);
    }

    /**
     * 结束当前 Span 并记录错误信息
     */
    public void finish(String errorCode, String errorMessage) {
        if (finished) {
            log.warn("当前链路已经结束: traceId={}, spanId={}", traceId, spanId);
            return;
        }

        this.endTime = System.currentTimeMillis();
        this.durationMs = endTime - startTime;

        if (errorCode != null || errorMessage != null) {
            this.statusCode = "ERROR";
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        } else {
            this.statusCode = "OK";
        }

        this.finished = true;

        log.debug("结束当前链路追踪: traceId={}, spanId={}, duration={}ms, status={}",
                traceId, spanId, durationMs, statusCode);
    }

    /**
     * 创建一个HTTP类型的Span
     */
    public static TraceSpan createHttpSpan(String traceId, String serviceName,
                                           String operationName, Long startTime,
                                           Long durationMs, Boolean success) {
        TraceSpan traceSpan = new TraceSpan();
        traceSpan.setTraceId(traceId);
        traceSpan.setSpanId(generateId());
        traceSpan.setServiceName(serviceName);
        traceSpan.setOperationName(operationName);
        traceSpan.setSpanKind("HTTP");
        traceSpan.setStartTime(startTime);
        traceSpan.setEndTime(startTime + durationMs);
        traceSpan.setDurationMs(durationMs);
        traceSpan.setSuccess(success);
        return traceSpan;
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
    public TraceSpan addTag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }
}
