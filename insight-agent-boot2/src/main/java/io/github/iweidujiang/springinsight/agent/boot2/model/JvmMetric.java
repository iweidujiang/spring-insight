package io.github.iweidujiang.springinsight.agent.boot2.model;

/**
 * JVM 运行时指标点，字段与主线 / Server 接收模型对齐。
 *
 * @author 苏渡苇
 * @since 2026-11-09
 */
public class JvmMetric {

    private String serviceName;
    private String serviceInstance;
    private String hostIp;
    private Integer hostPort;
    private Long timestamp;

    private long heapMemoryUsed;
    private long heapMemoryMax;
    private long heapMemoryCommitted;
    private long heapMemoryInit;

    private long nonHeapMemoryUsed;
    private long nonHeapMemoryMax;
    private long nonHeapMemoryCommitted;
    private long nonHeapMemoryInit;

    private long gcCount;
    private long gcTime;
    private long youngGcCount;
    private long youngGcTime;
    private long oldGcCount;
    private long oldGcTime;

    private int activeThreads;
    private int peakThreads;
    private int daemonThreads;
    private long totalThreads;

    private long loadedClasses;
    private long unloadedClasses;

    private String jvmName;
    private String jvmVersion;
    private String jvmVendor;
    private String javaVersion;

    private double systemLoadAverage;
    private int availableProcessors;

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getServiceInstance() { return serviceInstance; }
    public void setServiceInstance(String serviceInstance) { this.serviceInstance = serviceInstance; }
    public String getHostIp() { return hostIp; }
    public void setHostIp(String hostIp) { this.hostIp = hostIp; }
    public Integer getHostPort() { return hostPort; }
    public void setHostPort(Integer hostPort) { this.hostPort = hostPort; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public long getHeapMemoryUsed() { return heapMemoryUsed; }
    public void setHeapMemoryUsed(long heapMemoryUsed) { this.heapMemoryUsed = heapMemoryUsed; }
    public long getHeapMemoryMax() { return heapMemoryMax; }
    public void setHeapMemoryMax(long heapMemoryMax) { this.heapMemoryMax = heapMemoryMax; }
    public long getHeapMemoryCommitted() { return heapMemoryCommitted; }
    public void setHeapMemoryCommitted(long heapMemoryCommitted) { this.heapMemoryCommitted = heapMemoryCommitted; }
    public long getHeapMemoryInit() { return heapMemoryInit; }
    public void setHeapMemoryInit(long heapMemoryInit) { this.heapMemoryInit = heapMemoryInit; }

    public long getNonHeapMemoryUsed() { return nonHeapMemoryUsed; }
    public void setNonHeapMemoryUsed(long nonHeapMemoryUsed) { this.nonHeapMemoryUsed = nonHeapMemoryUsed; }
    public long getNonHeapMemoryMax() { return nonHeapMemoryMax; }
    public void setNonHeapMemoryMax(long nonHeapMemoryMax) { this.nonHeapMemoryMax = nonHeapMemoryMax; }
    public long getNonHeapMemoryCommitted() { return nonHeapMemoryCommitted; }
    public void setNonHeapMemoryCommitted(long nonHeapMemoryCommitted) { this.nonHeapMemoryCommitted = nonHeapMemoryCommitted; }
    public long getNonHeapMemoryInit() { return nonHeapMemoryInit; }
    public void setNonHeapMemoryInit(long nonHeapMemoryInit) { this.nonHeapMemoryInit = nonHeapMemoryInit; }

    public long getGcCount() { return gcCount; }
    public void setGcCount(long gcCount) { this.gcCount = gcCount; }
    public long getGcTime() { return gcTime; }
    public void setGcTime(long gcTime) { this.gcTime = gcTime; }
    public long getYoungGcCount() { return youngGcCount; }
    public void setYoungGcCount(long youngGcCount) { this.youngGcCount = youngGcCount; }
    public long getYoungGcTime() { return youngGcTime; }
    public void setYoungGcTime(long youngGcTime) { this.youngGcTime = youngGcTime; }
    public long getOldGcCount() { return oldGcCount; }
    public void setOldGcCount(long oldGcCount) { this.oldGcCount = oldGcCount; }
    public long getOldGcTime() { return oldGcTime; }
    public void setOldGcTime(long oldGcTime) { this.oldGcTime = oldGcTime; }

    public int getActiveThreads() { return activeThreads; }
    public void setActiveThreads(int activeThreads) { this.activeThreads = activeThreads; }
    public int getPeakThreads() { return peakThreads; }
    public void setPeakThreads(int peakThreads) { this.peakThreads = peakThreads; }
    public int getDaemonThreads() { return daemonThreads; }
    public void setDaemonThreads(int daemonThreads) { this.daemonThreads = daemonThreads; }
    public long getTotalThreads() { return totalThreads; }
    public void setTotalThreads(long totalThreads) { this.totalThreads = totalThreads; }

    public long getLoadedClasses() { return loadedClasses; }
    public void setLoadedClasses(long loadedClasses) { this.loadedClasses = loadedClasses; }
    public long getUnloadedClasses() { return unloadedClasses; }
    public void setUnloadedClasses(long unloadedClasses) { this.unloadedClasses = unloadedClasses; }

    public String getJvmName() { return jvmName; }
    public void setJvmName(String jvmName) { this.jvmName = jvmName; }
    public String getJvmVersion() { return jvmVersion; }
    public void setJvmVersion(String jvmVersion) { this.jvmVersion = jvmVersion; }
    public String getJvmVendor() { return jvmVendor; }
    public void setJvmVendor(String jvmVendor) { this.jvmVendor = jvmVendor; }
    public String getJavaVersion() { return javaVersion; }
    public void setJavaVersion(String javaVersion) { this.javaVersion = javaVersion; }

    public double getSystemLoadAverage() { return systemLoadAverage; }
    public void setSystemLoadAverage(double systemLoadAverage) { this.systemLoadAverage = systemLoadAverage; }
    public int getAvailableProcessors() { return availableProcessors; }
    public void setAvailableProcessors(int availableProcessors) { this.availableProcessors = availableProcessors; }
}
