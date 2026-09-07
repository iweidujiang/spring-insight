package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Boot 2.7 兼容线入口配置（由 {@code META-INF/spring.factories} 注册）。
 * <p>B0 骨架：仅占位装配；B1 起逐步迁入 TraceSpan / HTTP / HttpSink。</p>
 */
@Configuration
@EnableConfigurationProperties(InsightBoot2Properties.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightBoot2AutoConfiguration {
}
