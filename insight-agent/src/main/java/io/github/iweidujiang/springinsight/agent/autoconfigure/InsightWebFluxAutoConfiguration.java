package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.instrumentation.ReactiveInsightWebFilter;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

/**
 * Spring Cloud Gateway 等 WebFlux 应用下的 HTTP 入口追踪（不引入 spring-webmvc）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({WebFilter.class, reactor.core.publisher.Mono.class})
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightWebFluxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveInsightWebFilter.class)
    @ConditionalOnProperty(prefix = "spring.insight", name = "http-tracing-enabled", havingValue = "true", matchIfMissing = true)
    public ReactiveInsightWebFilter reactiveInsightWebFilter(SpanReportingListener spanReportingListener,
                                                             InsightProperties insightProperties) {
        return new ReactiveInsightWebFilter(spanReportingListener, insightProperties);
    }
}
