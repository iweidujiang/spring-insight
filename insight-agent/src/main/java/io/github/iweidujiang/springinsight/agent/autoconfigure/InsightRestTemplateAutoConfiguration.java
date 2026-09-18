package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.instrumentation.InsightClientHttpRequestInterceptor;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 出站 CLIENT Span：对 {@code RestTemplateBuilder} 构建的实例注入拦截器。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({RestTemplate.class, RestTemplateCustomizer.class})
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "spring.insight", name = "http-tracing-enabled", havingValue = "true", matchIfMissing = true)
public class InsightRestTemplateAutoConfiguration {

    /**
     * @param spanReportingListener 上报
     * @param insightProperties     配置
     * @return Builder 定制器（手写 {@code new RestTemplate()} 不生效）
     */
    @Bean
    @ConditionalOnMissingBean(name = "insightRestTemplateCustomizer")
    public RestTemplateCustomizer insightRestTemplateCustomizer(
            SpanReportingListener spanReportingListener,
            InsightProperties insightProperties) {
        InsightClientHttpRequestInterceptor interceptor =
                new InsightClientHttpRequestInterceptor(spanReportingListener, insightProperties, "RestTemplate");
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }
}
