package io.github.iweidujiang.springinsight.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
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

    /**
     * 是否纳入采样（头部采样决策）；未采样的 Span 不上报，出站 flags 写 {@code 00}。
     */
    @JsonIgnore
    private volatile boolean sampled = true;

    /** 创建时间（用于内部管理） */
    @JsonIgnore
    private final Instant createTime = Instant.now();

    /** 用于 W3C 兼容的 TraceId / SpanId 随机源 */
    private static final SecureRandom ID_RANDOM = new SecureRandom();

    /**
     * 创建一个新的 TraceSpan（根Span）；ID 符合 W3C：TraceId 32 hex、SpanId 16 hex。
     */
    public TraceSpan() {
        this.traceId = generateTraceId();
        this.spanId = generateSpanId();
        this.startTime = System.currentTimeMillis();
        log.debug("创建一个新的 TraceSpan: traceId={}, spanId={}", traceId, spanId);
    }

    /**
     * 创建一个子 Span（或跨服务延续的 SERVER Span）。
     *
     * @param traceId       共享 TraceId
     * @param parentSpanId  父 SpanId
     */
    public TraceSpan(String traceId, String parentSpanId) {
        if (traceId == null || traceId.trim().isEmpty()) {
            throw new IllegalArgumentException("TraceId 不能为空");
        }

        this.traceId = traceId;
        this.parentSpanId = parentSpanId;
        this.spanId = generateSpanId();
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
            this.success = false;
        } else {
            this.statusCode = "OK";
            this.success = true;
        }

        this.finished = true;

        log.debug("结束当前链路追踪: traceId={}, spanId={}, duration={}ms, status={}, success={}",
                traceId, spanId, durationMs, statusCode, success);
    }

    /**
     * 创建一个HTTP类型的Span
     */
    public static TraceSpan createHttpSpan(String traceId, String serviceName,
                                           String operationName, Long startTime,
                                           Long durationMs, Boolean success) {
        TraceSpan traceSpan = new TraceSpan();
        traceSpan.setTraceId(traceId);
        traceSpan.setSpanId(generateSpanId());
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
     * 生成 SpanId（16 位小写十六进制）。兼容旧调用方。
     *
     * @return 非全 0 的 16 hex
     */
    public static String generateId() {
        return generateSpanId();
    }

    /**
     * 生成 W3C TraceId（32 位小写十六进制，非全 0）。
     *
     * @return TraceId
     */
    public static String generateTraceId() {
        return randomHex(16);
    }

    /**
     * 生成 W3C SpanId（16 位小写十六进制，非全 0）。
     *
     * @return SpanId
     */
    public static String generateSpanId() {
        return randomHex(8);
    }

    /**
     * @param numBytes 随机字节数（TraceId=16，SpanId=8）
     * @return 对应长度的小写 hex，且不全为 0
     */
    private static String randomHex(int numBytes) {
        byte[] bytes = new byte[numBytes];
        // 避免 W3C 禁止的全 0 ID
        do {
            ID_RANDOM.nextBytes(bytes);
        } while (isAllZero(bytes));
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * @param bytes 待检查数组
     * @return 是否全为 0
     */
    private static boolean isAllZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 添加标签
     */
    public TraceSpan addTag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }

    /**
     * 生成快照副本，避免存入内存队列后仍被上游修改
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
            t.setTags(new HashMap<>(s.getTags()));
        }
        t.finished = s.finished;
        t.sampled = s.sampled;
        return t;
    }
}
