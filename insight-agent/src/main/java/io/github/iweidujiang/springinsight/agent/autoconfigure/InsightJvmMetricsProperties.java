package io.github.iweidujiang.springinsight.agent.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Insight JVM 指标配置类
 */
@Data
@ConfigurationProperties(prefix = "spring.insight.jvm-metrics")
public class InsightJvmMetricsProperties {
    /**
     * 是否启用JVM指标收集
     */
    private boolean enabled = true;
    
    /**
     * JVM指标上报间隔（毫秒）
     */
    private int reportInterval = 30000;
    
    /**
     * 是否收集堆内存指标
     */
    private boolean heapMemoryEnabled = true;
    
    /**
     * 是否收集非堆内存指标
     */
    private boolean nonHeapMemoryEnabled = true;
    
    /**
     * 是否收集垃圾回收指标
     */
    private boolean gcEnabled = true;
    
    /**
     * 是否收集线程指标
     */
    private boolean threadsEnabled = true;
    
    /**
     * 是否收集类加载指标
     */
    private boolean classLoadingEnabled = true;
    
    /**
     * 是否收集系统指标
     */
    private boolean systemEnabled = true;
}