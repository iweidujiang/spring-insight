package io.github.iweidujiang.springinsight.agent.boot2.collector;

import io.github.iweidujiang.springinsight.agent.boot2.model.JvmMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 使用 JMX 采集 JVM 运行时指标。
 *
 * @author 苏渡苇
 * @since 2026-11-09
 */
public class JvmMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(JvmMetricsCollector.class);

    private final String serviceName;
    private final String serviceInstance;
    private final String hostIp;
    private final Integer hostPort;

    private final RuntimeMXBean runtimeMXBean;
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final ClassLoadingMXBean classLoadingMXBean;
    private final List<GarbageCollectorMXBean> garbageCollectorMXBeans;
    private final OperatingSystemMXBean operatingSystemMXBean;

    /**
     * @param serviceName     服务名
     * @param serviceInstance 实例标识
     * @param hostPort        宿主端口（可空）
     */
    public JvmMetricsCollector(String serviceName, String serviceInstance, Integer hostPort) {
        this.serviceName = serviceName;
        this.serviceInstance = serviceInstance;
        this.hostPort = hostPort;
        this.hostIp = resolveHostIp();
        this.runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
        this.garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        log.info("[JVM指标收集器-Boot2] 初始化完成，服务名称: {}, 服务实例: {}", serviceName, serviceInstance);
    }

    /**
     * @return 当前时刻的 JVM 指标点
     */
    public JvmMetric collectMetrics() {
        JvmMetric metric = new JvmMetric();
        metric.setServiceName(serviceName);
        metric.setServiceInstance(serviceInstance);
        metric.setHostIp(hostIp);
        metric.setHostPort(hostPort);
        metric.setTimestamp(Long.valueOf(System.currentTimeMillis()));

        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        metric.setHeapMemoryUsed(heap.getUsed());
        metric.setHeapMemoryMax(heap.getMax());
        metric.setHeapMemoryCommitted(heap.getCommitted());
        metric.setHeapMemoryInit(heap.getInit());

        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
        metric.setNonHeapMemoryUsed(nonHeap.getUsed());
        metric.setNonHeapMemoryMax(nonHeap.getMax());
        metric.setNonHeapMemoryCommitted(nonHeap.getCommitted());
        metric.setNonHeapMemoryInit(nonHeap.getInit());

        long totalGcCount = 0L;
        long totalGcTime = 0L;
        long youngGcCount = 0L;
        long youngGcTime = 0L;
        long oldGcCount = 0L;
        long oldGcTime = 0L;
        for (int i = 0; i < garbageCollectorMXBeans.size(); i++) {
            GarbageCollectorMXBean gcBean = garbageCollectorMXBeans.get(i);
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            if (count < 0) {
                count = 0L;
            }
            if (time < 0) {
                time = 0L;
            }
            totalGcCount += count;
            totalGcTime += time;
            String name = gcBean.getName() == null ? "" : gcBean.getName();
            if (name.contains("Young") || name.contains("Eden") || name.contains("PS Scavenge")
                    || name.contains("G1 Young") || name.contains("ParNew")) {
                youngGcCount += count;
                youngGcTime += time;
            } else if (name.contains("Old") || name.contains("Tenured") || name.contains("PS MarkSweep")
                    || name.contains("G1 Old") || name.contains("CMS")) {
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

        metric.setActiveThreads(threadMXBean.getThreadCount());
        metric.setPeakThreads(threadMXBean.getPeakThreadCount());
        metric.setDaemonThreads(threadMXBean.getDaemonThreadCount());
        metric.setTotalThreads(threadMXBean.getTotalStartedThreadCount());

        metric.setLoadedClasses(classLoadingMXBean.getLoadedClassCount());
        metric.setUnloadedClasses(classLoadingMXBean.getUnloadedClassCount());

        metric.setJvmName(runtimeMXBean.getVmName());
        metric.setJvmVersion(runtimeMXBean.getVmVersion());
        metric.setJvmVendor(runtimeMXBean.getVmVendor());
        metric.setJavaVersion(System.getProperty("java.version"));

        metric.setAvailableProcessors(operatingSystemMXBean.getAvailableProcessors());
        metric.setSystemLoadAverage(operatingSystemMXBean.getSystemLoadAverage());
        return metric;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceInstance() {
        return serviceInstance;
    }

    private static String resolveHostIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }
}
