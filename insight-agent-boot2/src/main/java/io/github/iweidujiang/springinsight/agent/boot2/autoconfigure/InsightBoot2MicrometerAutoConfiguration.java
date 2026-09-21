/**
 * InsightBoot2MicrometerAutoConfiguration：宿主存在 MeterRegistry 时注册 Insight 指标（Boot2）。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import io.github.iweidujiang.springinsight.agent.boot2.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.boot2.micrometer.InsightMicrometerBridge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 连接池 / JVM 详细指标仍走 Actuator；此处只曝光 Span 耗时与上报队列。
 */
@Configuration
@AutoConfigureAfter(name = {
        "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration"
})
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean({MeterRegistry.class, AsyncSpanReporter.class})
@ConditionalOnExpression("${spring.insight.enabled:true} and ${spring.insight.micrometer-enabled:true}")
public class InsightBoot2MicrometerAutoConfiguration {

    /**
     * 注册 Micrometer 桥接 Bean。
     *
     * @param meterRegistry     宿主注册表
     * @param asyncSpanReporter 异步上报器
     * @return Bridge
     */
    @Bean
    @ConditionalOnMissingBean(InsightMicrometerBridge.class)
    public InsightMicrometerBridge insightMicrometerBridge(MeterRegistry meterRegistry,
                                                           AsyncSpanReporter asyncSpanReporter) {
        return new InsightMicrometerBridge(meterRegistry, asyncSpanReporter);
    }
}
