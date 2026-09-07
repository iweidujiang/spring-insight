/*
 * Copyright (c) 2026, 苏渡苇. All rights reserved.
 *
 * InsightBoot2AutoConfiguration：Boot2 自动装配（spring.factories）；注册上报、HTTP 拦截。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.boot2.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.boot2.instrumentation.HttpRequestInterceptor;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.sink.HttpInsightBatchSink;
import io.github.iweidujiang.springinsight.agent.boot2.sink.InsightBatchSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
@EnableConfigurationProperties(InsightBoot2Properties.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightBoot2AutoConfiguration {

    private final InsightBoot2Properties properties;

    public InsightBoot2AutoConfiguration(InsightBoot2Properties properties, Environment environment) {
        this.properties = properties;
        properties.resolveServiceNameFromEnvironment(environment);
        properties.validate();
        log.info("[Boot2装配] Spring Insight Boot2 Agent 就绪: serviceName={}, serverUrl={}",
                properties.getServiceName(),
                properties.hasServerUrl() ? properties.normalizeServerUrl() : "(none)");
    }

    @Bean
    @ConditionalOnMissingBean(InsightBatchSink.class)
    @ConditionalOnProperty(prefix = "spring.insight", name = "server-url")
    public InsightBatchSink httpInsightBatchSink(ObjectMapper objectMapper) {
        if (!StringUtils.hasText(properties.normalizeServerUrl())) {
            throw new IllegalStateException("spring.insight.server-url 已声明但值为空");
        }
        return new HttpInsightBatchSink(properties, objectMapper);
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    public AsyncSpanReporter asyncSpanReporter(ObjectProvider<InsightBatchSink> batchSinkProvider) {
        AsyncSpanReporter reporter = new AsyncSpanReporter(properties.getServiceName(), batchSinkProvider);
        reporter.start();
        return reporter;
    }

    @Bean
    @ConditionalOnMissingBean
    public SpanReportingListener spanReportingListener(AsyncSpanReporter asyncSpanReporter) {
        return new SpanReportingListener(asyncSpanReporter);
    }

    @Configuration
    @ConditionalOnWebApplication
    @ConditionalOnClass(WebMvcConfigurer.class)
    @ConditionalOnProperty(prefix = "spring.insight", name = "http-tracing-enabled", havingValue = "true", matchIfMissing = true)
    static class MvcTracingConfiguration implements WebMvcConfigurer {

        private final InsightBoot2Properties properties;
        private final ObjectProvider<HttpRequestInterceptor> interceptorProvider;

        MvcTracingConfiguration(InsightBoot2Properties properties,
                                ObjectProvider<HttpRequestInterceptor> interceptorProvider) {
            this.properties = properties;
            this.interceptorProvider = interceptorProvider;
        }

        @Bean
        @ConditionalOnMissingBean
        public HttpRequestInterceptor httpRequestInterceptor(SpanReportingListener spanReportingListener,
                                                             InsightBoot2Properties props) {
            return new HttpRequestInterceptor(spanReportingListener, props);
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            HttpRequestInterceptor interceptor = interceptorProvider.getIfAvailable();
            if (interceptor != null && properties.isHttpTracingEnabled()) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns(properties.resolveExcludePatterns());
                log.info("[Boot2装配] HTTP 拦截器已注册");
            }
        }
    }
}
