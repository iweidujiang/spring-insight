/**
 * Boot2 Feign 出站追踪自动配置：包装 Feign {@link feign.Client} 以上报 CLIENT Span。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.feign;

import feign.Client;
import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass(Client.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightBoot2FeignAutoConfiguration {

    /**
     * 注册后置处理器：将容器中的 Feign {@link Client} 包一层 {@link TracingFeignClient}。
     *
     * @param insightProperties     Insight 配置
     * @param spanReportingListener Span 上报入口
     * @return BeanPostProcessor
     */
    @Bean
    public static BeanPostProcessor insightBoot2FeignTracingBeanPostProcessor(
            final ObjectProvider<InsightBoot2Properties> insightProperties,
            final ObjectProvider<SpanReportingListener> spanReportingListener) {
        return new BeanPostProcessor() {
            /**
             * Bean 初始化完成后包装 Feign Client（避免重复包装 TracingFeignClient）。
             *
             * @param bean     原始 Bean
             * @param beanName Bean 名称
             * @return 原始 Bean 或包装后的 Client
             */
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof Client) || bean instanceof TracingFeignClient) {
                    return bean;
                }
                // Boot2 demo 可能使用 url= 直连（无 LoadBalancer），故包装所有 Client
                return new TracingFeignClient((Client) bean, insightProperties, spanReportingListener);
            }
        };
    }
}
