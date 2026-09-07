package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import io.github.iweidujiang.springinsight.agent.boot2.instrumentation.InsightWebClientExchangeFilter;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Boot2 WebClient 出站 CLIENT Span：对 Spring 管理的 WebClient.Builder 注入 Filter。
 *
 * @since 2026-09-07
 * @author 公众号：苏渡苗 GitHub：https://github.com/iweidujiang
 */
@Configuration
@ConditionalOnClass({WebClient.class, WebClientCustomizer.class})
@ConditionalOnExpression("${spring.insight.enabled:true} and ${spring.insight.http-tracing-enabled:true}")
public class InsightBoot2WebClientAutoConfiguration {

    /**
     * WebClient ExchangeFilter Bean。
     *
     * @param spanReportingListener 上报入口
     * @param insightProperties     配置
     * @return Filter
     */
    @Bean
    @ConditionalOnMissingBean(InsightWebClientExchangeFilter.class)
    public InsightWebClientExchangeFilter insightWebClientExchangeFilter(
            SpanReportingListener spanReportingListener,
            InsightBoot2Properties insightProperties) {
        return new InsightWebClientExchangeFilter(spanReportingListener, insightProperties);
    }

    /**
     * 注入到所有 WebClient.Builder（手写 WebClient.create() 不生效）。
     *
     * @param filter ExchangeFilter
     * @return Customizer
     */
    @Bean
    @ConditionalOnMissingBean(name = "insightBoot2WebClientCustomizer")
    public WebClientCustomizer insightBoot2WebClientCustomizer(final InsightWebClientExchangeFilter filter) {
        return new WebClientCustomizer() {
            @Override
            public void customize(WebClient.Builder webClientBuilder) {
                webClientBuilder.filter(filter);
            }
        };
    }
}
