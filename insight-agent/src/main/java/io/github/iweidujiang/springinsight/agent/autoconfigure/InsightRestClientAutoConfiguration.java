package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.instrumentation.InsightClientHttpRequestInterceptor;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClient 出站 CLIENT Span：对 Boot 管理的 {@link RestClient.Builder} 注入拦截器。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({RestClient.class, RestClientCustomizer.class})
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "spring.insight", name = "http-tracing-enabled", havingValue = "true", matchIfMissing = true)
public class InsightRestClientAutoConfiguration {

    /**
     * @param spanReportingListener 上报
     * @param insightProperties     配置
     * @return Builder 定制器（手写 {@code RestClient.create()} 不生效）
     */
    @Bean
    @ConditionalOnMissingBean(name = "insightRestClientCustomizer")
    public RestClientCustomizer insightRestClientCustomizer(
            SpanReportingListener spanReportingListener,
            InsightProperties insightProperties) {
        InsightClientHttpRequestInterceptor interceptor =
                new InsightClientHttpRequestInterceptor(spanReportingListener, insightProperties, "RestClient");
        return builder -> builder.requestInterceptor(interceptor);
    }
}
