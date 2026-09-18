package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import io.github.iweidujiang.springinsight.agent.boot2.instrumentation.InsightClientHttpRequestInterceptor;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Boot2 RestTemplate 出站 CLIENT Span：对 {@code RestTemplateBuilder} 构建的实例注入拦截器。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Configuration
@ConditionalOnClass({RestTemplate.class, RestTemplateCustomizer.class})
@ConditionalOnExpression("${spring.insight.enabled:true} and ${spring.insight.http-tracing-enabled:true}")
public class InsightBoot2RestTemplateAutoConfiguration {

    /**
     * @param spanReportingListener 上报
     * @param insightProperties     配置
     * @return Builder 定制器（手写 {@code new RestTemplate()} 不生效）
     */
    @Bean
    @ConditionalOnMissingBean(name = "insightBoot2RestTemplateCustomizer")
    public RestTemplateCustomizer insightBoot2RestTemplateCustomizer(
            final SpanReportingListener spanReportingListener,
            final InsightBoot2Properties insightProperties) {
        final InsightClientHttpRequestInterceptor interceptor =
                new InsightClientHttpRequestInterceptor(spanReportingListener, insightProperties, "RestTemplate");
        return new RestTemplateCustomizer() {
            @Override
            public void customize(RestTemplate restTemplate) {
                restTemplate.getInterceptors().add(interceptor);
            }
        };
    }
}
