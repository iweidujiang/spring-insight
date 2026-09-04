package io.github.iweidujiang.springinsight.agent.collector;

import io.github.iweidujiang.springinsight.agent.model.JvmMetric;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * JVM 指标收集器
 * 使用JMX API收集JVM运行时的各项指标
 */
@Slf4j
public class JvmMetricsCollector {

    private final String serviceName;
    private final String serviceInstance;
    private final String hostIp;
    private final Integer hostPort;

    // JMX MBeans
    private final RuntimeMXBean runtimeMXBean;
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final ClassLoadingMXBean classLoadingMXBean;
    private final List<GarbageCollectorMXBean> garbageCollectorMXBeans;
    private final OperatingSystemMXBean operatingSystemMXBean;

    public JvmMetricsCollector(String serviceName, String serviceInstance, Integer hostPort) {
        this.serviceName = serviceName;
        this.serviceInstance = serviceInstance;
        this.hostPort = hostPort;
        this.hostIp = getHostIp();

        // 初始化JMX MBeans
        this.runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
        this.garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();

        log.info("[JVM指标收集器] 初始化完成，服务名称: {}, 服务实例: {}", serviceName, serviceInstance);
    }

    /**
     * 采集JVM指标
     */
    public JvmMetric collectMetrics() {
        log.debug("[JVM指标收集器] 开始采集JVM指标");

        JvmMetric metric = new JvmMetric();
        long timestamp = System.currentTimeMillis();

        // 基础信息
        metric.setServiceName(serviceName);
        metric.setServiceInstance(serviceInstance);
        metric.setHostIp(hostIp);
        metric.setHostPort(hostPort);
        metric.setTimestamp(timestamp);

        // 内存指标
        collectMemoryMetrics(metric);

        // 垃圾回收指标
        collectGarbageCollectionMetrics(metric);

        // 线程指标
        collectThreadMetrics(metric);

        // 类加载指标
        collectClassLoadingMetrics(metric);

        // JVM版本信息
        collectJvmVersionMetrics(metric);

        // 系统指标
        collectSystemMetrics(metric);

        log.debug("[JVM指标收集器] JVM指标采集完成");
        return metric;
    }

    /**
     * 采集内存指标
     */
    private void collectMemoryMetrics(JvmMetric metric) {
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        metric.setHeapMemoryUsed(heapMemoryUsage.getUsed());
        metric.setHeapMemoryMax(heapMemoryUsage.getMax());
        metric.setHeapMemoryCommitted(heapMemoryUsage.getCommitted());
        metric.setHeapMemoryInit(heapMemoryUsage.getInit());

        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        metric.setNonHeapMemoryUsed(nonHeapMemoryUsage.getUsed());
        metric.setNonHeapMemoryMax(nonHeapMemoryUsage.getMax());
        metric.setNonHeapMemoryCommitted(nonHeapMemoryUsage.getCommitted());
        metric.setNonHeapMemoryInit(nonHeapMemoryUsage.getInit());
    }

    /**
     * 采集垃圾回收指标
     */
    private void collectGarbageCollectionMetrics(JvmMetric metric) {
        long totalGcCount = 0;
        long totalGcTime = 0;
        long youngGcCount = 0;
        long youngGcTime = 0;
        long oldGcCount = 0;
        long oldGcTime = 0;

        for (GarbageCollectorMXBean gcBean : garbageCollectorMXBeans) {
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            totalGcCount += count;
            totalGcTime += time;

            String name = gcBean.getName();
            if (name.contains("Young") || name.contains("Eden") || name.contains("PS Scavenge") || name.contains("G1 Young") || name.contains("ParNew")) {
                youngGcCount += count;
                youngGcTime += time;
            } else if (name.contains("Old") || name.contains("Tenured") || name.contains("PS MarkSweep") || name.contains("G1 Old") || name.contains("CMS")) {
                oldGcCount += count;
                oldGcTime += time;
            }
        }

        metric.setGcCount(totalGcCount);
        metric.setGcTime(totalGcTime);
        metric.setYoungGcCount(youngGcCount);
        metric.setYoungGcTime(youngGcTime);
        metric.setOldGcCount(oldGcCount);
        metric.setOldGcTime(oldGcTime);
    }

    /**
     * 采集线程指标
     */
    private void collectThreadMetrics(JvmMetric metric) {
        metric.setActiveThreads(threadMXBean.getThreadCount());
        metric.setPeakThreads(threadMXBean.getPeakThreadCount());
        metric.setDaemonThreads(threadMXBean.getDaemonThreadCount());
        metric.setTotalThreads(threadMXBean.getTotalStartedThreadCount());
    }

    /**
     * 采集类加载指标
     */
    private void collectClassLoadingMetrics(JvmMetric metric) {
        metric.setLoadedClasses(classLoadingMXBean.getLoadedClassCount());
        metric.setUnloadedClasses(classLoadingMXBean.getUnloadedClassCount());
    }

    /**
     * 采集JVM版本信息
     */
    private void collectJvmVersionMetrics(JvmMetric metric) {
        metric.setJvmName(runtimeMXBean.getVmName());
        metric.setJvmVersion(runtimeMXBean.getVmVersion());
        metric.setJvmVendor(runtimeMXBean.getVmVendor());
        metric.setJavaVersion(System.getProperty("java.version"));
    }

    /**
     * 采集系统指标
     */
    private void collectSystemMetrics(JvmMetric metric) {
        metric.setAvailableProcessors(operatingSystemMXBean.getAvailableProcessors());
        metric.setSystemLoadAverage(getSystemLoadAverage());
    }

    /**
     * 获取系统负载平均值
     */
    private double getSystemLoadAverage() {
        if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsMxBean = 
                    (com.sun.management.OperatingSystemMXBean) operatingSystemMXBean;
            return sunOsMxBean.getSystemLoadAverage();
        }
        return 0.0;
    }

    /**
     * 获取主机IP
     */
    private String getHostIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("[JVM指标收集器] 获取主机IP失败，使用默认值: 127.0.0.1", e);
            return "127.0.0.1";
        }
    }

    /**
     * 获取服务名称
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * 获取服务实例
     */
    public String getServiceInstance() {
        return serviceInstance;
    }
}