package io.github.iweidujiang.springinsight.agent.model;

import lombok.Data;

import java.util.Map;

/**
 * 数据库调用模型
 * 收集数据库调用的各项指标和信息
 */
@Data
public class DbCall {
    // ========== 基础信息 ==========
    /** 服务名称 */
    private String serviceName;
    /** 服务实例 */
    private String serviceInstance;
    /** 主机IP */
    private String hostIp;
    /** 主机端口 */
    private Integer hostPort;
    /** 采集时间戳 */
    private Long timestamp;
    /** 追踪ID */
    private String traceId;
    /** 父SpanID */
    private String parentSpanId;
    /** 当前SpanID */
    private String spanId;

    // ========== 数据库信息 ==========
    /** 数据库类型（如：mysql, postgresql, oracle） */
    private String dbType;
    /** 数据库URL */
    private String dbUrl;
    /** 数据库名称 */
    private String dbName;
    /** 数据库用户名 */
    private String dbUser;
    /** 数据库主机 */
    private String dbHost;
    /** 数据库端口 */
    private Integer dbPort;

    // ========== 调用信息 ==========
    /** SQL语句 */
    private String sql;
    /** SQL类型（SELECT, INSERT, UPDATE, DELETE, EXECUTE等） */
    private String sqlType;
    /** SQL参数 */
    private Map<String, Object> sqlParams;
    /** SQL执行开始时间 */
    private Long startTime;
    /** SQL执行结束时间 */
    private Long endTime;
    /** SQL执行持续时间（毫秒） */
    private Long durationMs;
    /** 影响行数 */
    private Integer affectedRows;
    /** 结果集大小 */
    private Integer resultSetSize;
    /** 是否成功 */
    private Boolean success;
    /** 错误信息 */
    private String errorMessage;
    /** 错误码 */
    private String errorCode;

    // ========== 调用上下文 ==========
    /** 应用方法名 */
    private String applicationMethod;
    /** 应用类名 */
    private String applicationClass;
    /** 调用栈信息 */
    private String callStack;
}