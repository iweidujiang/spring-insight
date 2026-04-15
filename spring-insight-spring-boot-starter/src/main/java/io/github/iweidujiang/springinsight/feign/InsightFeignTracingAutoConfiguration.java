package io.github.iweidujiang.springinsight.feign;

import feign.Client;
import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 包装 Spring Cloud 暴露的 Feign {@link Client}，上报 CLIENT Span（含 {@code remoteService}）。
 * <p>
 * 仅处理典型 {@code *LoadBalancer*} 实现，避免误包 {@code feign.Client.Default} 等底层委托导致重复打点。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(Client.class)
public class InsightFeignTracingAutoConfiguration {

    @Bean
    public static BeanPostProcessor insightFeignTracingBeanPostProcessor(
            ObjectProvider<InsightProperties> insightProperties,
            ObjectProvider<SpanReportingListener> spanReportingListener) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof Client) || bean instanceof TracingFeignClient) {
                    return bean;
                }
                String simple = bean.getClass().getSimpleName();
                if (!simple.contains("LoadBalancer")) {
                    return bean;
                }
                return new TracingFeignClient((Client) bean, insightProperties, spanReportingListener);
            }
        };
    }
}
