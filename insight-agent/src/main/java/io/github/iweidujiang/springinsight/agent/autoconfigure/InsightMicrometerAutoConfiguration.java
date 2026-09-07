package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.micrometer.InsightMicrometerBridge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 可选 Micrometer 联动：宿主存在 {@link MeterRegistry} 时注册 Insight 指标。
 * <p>
 * 连接池 / JVM 详细指标仍走 Actuator；此处只暴露 Span 耗时与上报器队列状态。
 * </p>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration"
})
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "spring.insight", name = "micrometer-enabled", havingValue = "true", matchIfMissing = true)
public class InsightMicrometerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(InsightMicrometerBridge.class)
    public InsightMicrometerBridge insightMicrometerBridge(MeterRegistry meterRegistry,
                                                           AsyncSpanReporter asyncSpanReporter) {
        return new InsightMicrometerBridge(meterRegistry, asyncSpanReporter);
    }
}
