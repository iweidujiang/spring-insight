package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.instrumentation.InsightGatewayTracingFilter;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cloud Gateway 出站 CLIENT Span（仅 Gateway  classpath 生效）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(GlobalFilter.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "spring.insight", name = "http-tracing-enabled", havingValue = "true", matchIfMissing = true)
public class InsightGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(InsightGatewayTracingFilter.class)
    public InsightGatewayTracingFilter insightGatewayTracingFilter(SpanReportingListener spanReportingListener,
                                                                   InsightProperties insightProperties) {
        return new InsightGatewayTracingFilter(spanReportingListener, insightProperties);
    }
}
