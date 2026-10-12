package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import io.github.iweidujiang.springinsight.agent.boot2.instrumentation.InsightRedisProxies;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Redis 追踪：包装 Spring 容器中的 {@link RedisConnectionFactory}（默认关闭）。
 * <p>
 * 开启条件：{@code spring.insight.redis-tracing-enabled=true}。
 * </p>
 *
 * @since 2026-10-12
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Configuration
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightRedisAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InsightRedisAutoConfiguration.class);

    /**
     * @param properties 配置
     * @param listener   上报
     * @return 工厂包装 BPP；未开启时返回 no-op
     */
    @Bean
    public static BeanPostProcessor insightRedisConnectionFactoryPostProcessor(
            InsightBoot2Properties properties,
            SpanReportingListener listener) {
        if (!properties.isRedisTracingEnabled()) {
            return new BeanPostProcessor() {
            };
        }
        log.info("[Redis] 已启用 RedisConnectionFactory 包装（慢阈值={}ms；错误始终上报）",
                Long.valueOf(properties.getRedisSlowThresholdMs()));
        return new InsightRedisFactoryPostProcessor(listener, properties);
    }

    private static final class InsightRedisFactoryPostProcessor implements BeanPostProcessor {
        private final SpanReportingListener listener;
        private final InsightBoot2Properties properties;

        InsightRedisFactoryPostProcessor(SpanReportingListener listener, InsightBoot2Properties properties) {
            this.listener = listener;
            this.properties = properties;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (!(bean instanceof RedisConnectionFactory) || InsightRedisProxies.isInsightWrapped(bean)) {
                return bean;
            }
            log.debug("[Redis] 包装 RedisConnectionFactory bean={}", beanName);
            return InsightRedisProxies.wrapFactory(bean, listener, properties);
        }
    }
}
