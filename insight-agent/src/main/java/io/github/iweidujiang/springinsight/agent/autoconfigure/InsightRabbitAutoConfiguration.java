package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.instrumentation.InsightRabbitProxies;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 生产端追踪：包装 Spring 容器中的 {@link RabbitTemplate}（默认关闭）。
 * <p>
 * 开启条件：{@code spring.insight.rabbit-tracing-enabled=true}。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026-11-28
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightRabbitAutoConfiguration {

    /**
     * @param properties 配置
     * @param listener   上报
     * @return RabbitTemplate 包装 BPP；未开启时返回 no-op
     */
    @Bean
    public static BeanPostProcessor insightRabbitTemplatePostProcessor(
            InsightProperties properties,
            SpanReportingListener listener) {
        if (!properties.isRabbitTracingEnabled()) {
            return new BeanPostProcessor() {
            };
        }
        log.info("[Rabbit] 已启用 RabbitTemplate 生产端包装（慢阈值={}ms；错误始终上报）",
                properties.getRabbitSlowThresholdMs());
        return new InsightRabbitTemplatePostProcessor(listener, properties);
    }

    private static final class InsightRabbitTemplatePostProcessor implements BeanPostProcessor {
        private final SpanReportingListener listener;
        private final InsightProperties properties;

        InsightRabbitTemplatePostProcessor(SpanReportingListener listener, InsightProperties properties) {
            this.listener = listener;
            this.properties = properties;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (!(bean instanceof RabbitTemplate template)) {
                return bean;
            }
            if (InsightRabbitProxies.isInsightWrapped(template)) {
                return template;
            }
            log.debug("[Rabbit] 包装 RabbitTemplate bean={}", beanName);
            return InsightRabbitProxies.wrap(template, listener, properties);
        }
    }
}
