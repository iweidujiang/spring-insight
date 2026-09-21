package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import io.github.iweidujiang.springinsight.agent.boot2.instrumentation.InsightBoot2GatewayTracingFilter;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boot2 Spring Cloud Gateway 出站 CLIENT Span（仅 Gateway classpath 生效）。
 *
 * @since 2026-09-07
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(GlobalFilter.class)
@ConditionalOnExpression("${spring.insight.enabled:true} and ${spring.insight.http-tracing-enabled:true}")
public class InsightBoot2GatewayAutoConfiguration {

    /**
     * 注册 Gateway GlobalFilter，代理下游时产生 CLIENT Span。
     *
     * @param spanReportingListener 上报入口
     * @param insightProperties     配置
     * @return Filter
     */
    @Bean
    @ConditionalOnMissingBean(InsightBoot2GatewayTracingFilter.class)
    public InsightBoot2GatewayTracingFilter insightBoot2GatewayTracingFilter(
            SpanReportingListener spanReportingListener,
            InsightBoot2Properties insightProperties) {
        return new InsightBoot2GatewayTracingFilter(spanReportingListener, insightProperties);
    }
}
