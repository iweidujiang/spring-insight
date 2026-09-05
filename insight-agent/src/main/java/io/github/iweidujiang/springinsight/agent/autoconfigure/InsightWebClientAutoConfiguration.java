package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.instrumentation.InsightWebClientExchangeFilter;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 出站 CLIENT Span：对 Spring Boot 管理的 {@link WebClient.Builder} 注入 ExchangeFilter。
 * <p>
 * 不限定 REACTIVE Web 应用——Servlet 业务里使用 WebClient 同样可挂父子链路。
 * </p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({WebClient.class, WebClientCustomizer.class})
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "spring.insight", name = "http-tracing-enabled", havingValue = "true", matchIfMissing = true)
public class InsightWebClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(InsightWebClientExchangeFilter.class)
    public InsightWebClientExchangeFilter insightWebClientExchangeFilter(
            SpanReportingListener spanReportingListener,
            InsightProperties insightProperties) {
        return new InsightWebClientExchangeFilter(spanReportingListener, insightProperties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "insightWebClientCustomizer")
    public WebClientCustomizer insightWebClientCustomizer(InsightWebClientExchangeFilter filter) {
        return builder -> builder.filter(filter);
    }
}
