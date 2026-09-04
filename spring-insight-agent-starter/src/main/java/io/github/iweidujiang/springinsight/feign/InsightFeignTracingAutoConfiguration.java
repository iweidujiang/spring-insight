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
 * 包装 Spring Cloud LoadBalancer Feign {@link Client}，上报带 {@code remoteService} 的 CLIENT Span。
 * <p>
 * 归属 {@code spring-insight-agent-starter}，业务侧引入 agent-starter 即可获得拓扑边能力。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/7/28
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(Client.class)
public class InsightFeignTracingAutoConfiguration {

    /**
     * 注册 BeanPostProcessor：在容器初始化后包装名称含 {@code LoadBalancer} 的 Feign Client。
     *
     * @param insightProperties      Insight 配置（延迟获取，避免启动顺序问题）
     * @param spanReportingListener  Span 上报入口
     * @return 用于包装 Feign Client 的后置处理器
     */
    @Bean
    public static BeanPostProcessor insightFeignTracingBeanPostProcessor(
            ObjectProvider<InsightProperties> insightProperties,
            ObjectProvider<SpanReportingListener> spanReportingListener) {
        return new BeanPostProcessor() {
            /**
             * Bean 初始化完成后：若为 LoadBalancer Feign Client 则包一层 {@link TracingFeignClient}。
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
                // 只包装 LoadBalancer 客户端，避免误包 Default 导致重复打点
                String simple = bean.getClass().getSimpleName();
                if (!simple.contains("LoadBalancer")) {
                    return bean;
                }
                return new TracingFeignClient((Client) bean, insightProperties, spanReportingListener);
            }
        };
    }
}
