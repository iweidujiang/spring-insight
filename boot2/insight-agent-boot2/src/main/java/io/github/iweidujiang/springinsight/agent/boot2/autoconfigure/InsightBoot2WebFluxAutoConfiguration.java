package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import io.github.iweidujiang.springinsight.agent.boot2.instrumentation.ReactiveInsightWebFilter;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * Boot2 WebFlux 入口追踪自动配置（仅 REACTIVE Web 应用）。
 *
 * @since 2026-09-07
 * @author 公众号：苏渡苗 GitHub：https://github.com/iweidujiang
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({WebFilter.class, Mono.class})
@ConditionalOnExpression("${spring.insight.enabled:true} and ${spring.insight.http-tracing-enabled:true}")
public class InsightBoot2WebFluxAutoConfiguration {

    /**
     * 注册 WebFlux HTTP SERVER Span Filter。
     *
     * @param spanReportingListener 上报入口
     * @param insightProperties     配置
     * @return Filter
     */
    @Bean
    @ConditionalOnMissingBean(ReactiveInsightWebFilter.class)
    public ReactiveInsightWebFilter reactiveInsightWebFilter(SpanReportingListener spanReportingListener,
                                                             InsightBoot2Properties insightProperties) {
        return new ReactiveInsightWebFilter(spanReportingListener, insightProperties);
    }
}
