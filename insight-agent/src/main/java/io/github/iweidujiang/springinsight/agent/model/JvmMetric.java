package io.github.iweidujiang.springinsight.agent.model;

import lombok.Data;

/**
 * JVM 指标模型
 * 收集JVM运行时的各项指标
 */
@Data
public class JvmMetric {
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

    // ========== 内存指标 ==========
    /** 堆内存使用量（字节） */
    private long heapMemoryUsed;
    /** 堆内存最大可用量（字节） */
    private long heapMemoryMax;
    /** 堆内存已提交量（字节） */
    private long heapMemoryCommitted;
    /** 堆内存初始量（字节） */
    private long heapMemoryInit;

    /** 非堆内存使用量（字节） */
    private long nonHeapMemoryUsed;
    /** 非堆内存最大可用量（字节） */
    private long nonHeapMemoryMax;
    /** 非堆内存已提交量（字节） */
    private long nonHeapMemoryCommitted;
    /** 非堆内存初始量（字节） */
    private long nonHeapMemoryInit;

    // ========== 垃圾回收指标 ==========
    /** GC次数 */
    private long gcCount;
    /** GC总时间（毫秒） */
    private long gcTime;
    /** 年轻代GC次数 */
    private long youngGcCount;
    /** 年轻代GC时间（毫秒） */
    private long youngGcTime;
    /** 老年代GC次数 */
    private long oldGcCount;
    /** 老年代GC时间（毫秒） */
    private long oldGcTime;

    // ========== 线程指标 ==========
    /** 活跃线程数 */
    private int activeThreads;
    /** 峰值线程数 */
    private int peakThreads;
    /** 守护线程数 */
    private int daemonThreads;
    /** 总线程数 */
    private long totalThreads;

    // ========== 类加载指标 ==========
    /** 已加载类数量 */
    private long loadedClasses;
    /** 未加载类数量 */
    private long unloadedClasses;

    // ========== JVM版本信息 ==========
    /** JVM名称 */
    private String jvmName;
    /** JVM版本 */
    private String jvmVersion;
    /** JVM供应商 */
    private String jvmVendor;
    /** Java版本 */
    private String javaVersion;

    // ========== 系统指标 ==========
    /** 系统负载平均值 */
    private double systemLoadAverage;
    /** CPU核心数 */
    private int availableProcessors;
}