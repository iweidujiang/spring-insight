/**
 * InsightBoot2AutoConfiguration?Boot2 ?????spring.factories??????? HTTP ???
 *
 * @since?2026-09-07
 * @author???? ???????
 *
 * GitHub?https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.boot2.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.boot2.instrumentation.HttpRequestInterceptor;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.sink.HttpInsightBatchSink;
import io.github.iweidujiang.springinsight.agent.boot2.sink.InsightBatchSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Configuration
@EnableConfigurationProperties(InsightBoot2Properties.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightBoot2AutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InsightBoot2AutoConfiguration.class);

    private final InsightBoot2Properties properties;

    /**
     * ???????????
     *
     * @param properties  Insight ??
     * @param environment Spring Environment
     */
    public InsightBoot2AutoConfiguration(InsightBoot2Properties properties, Environment environment) {
        this.properties = properties;
        properties.resolveServiceNameFromEnvironment(environment);
        properties.validate();
        log.info("[Boot2??] Spring Insight Boot2 Agent ??: serviceName={}, serverUrl={}",
                properties.getServiceName(),
                properties.hasServerUrl() ? properties.normalizeServerUrl() : "(none)");
    }

    /**
     * HTTP ???? Sink???? server-url??
     *
     * @param objectMapper Jackson
     * @return Sink
     */
    @Bean
    @ConditionalOnMissingBean(InsightBatchSink.class)
    @ConditionalOnProperty(prefix = "spring.insight", name = "server-url")
    public InsightBatchSink httpInsightBatchSink(ObjectMapper objectMapper) {
        if (!StringUtils.hasText(properties.normalizeServerUrl())) {
            throw new IllegalStateException("spring.insight.server-url ???????");
        }
        return new HttpInsightBatchSink(properties, objectMapper);
    }

    /**
     * ??????
     *
     * @param batchSinkProvider Sink ???
     * @return ? start ????
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    public AsyncSpanReporter asyncSpanReporter(ObjectProvider<InsightBatchSink> batchSinkProvider) {
        AsyncSpanReporter reporter = new AsyncSpanReporter(properties.getServiceName(), batchSinkProvider);
        reporter.start();
        return reporter;
    }

    /**
     * Span ??????
     *
     * @param asyncSpanReporter ?????
     * @return ???
     */
    @Bean
    @ConditionalOnMissingBean
    public SpanReportingListener spanReportingListener(AsyncSpanReporter asyncSpanReporter) {
        return new SpanReportingListener(asyncSpanReporter);
    }

    /**
     * MVC ??????Servlet Web ????
     */
    @Configuration
    @ConditionalOnWebApplication
    @ConditionalOnClass(WebMvcConfigurer.class)
    @ConditionalOnProperty(prefix = "spring.insight", name = "http-tracing-enabled", havingValue = "true", matchIfMissing = true)
    static class MvcTracingConfiguration implements WebMvcConfigurer {

        private static final Logger log = LoggerFactory.getLogger(MvcTracingConfiguration.class);

        private final InsightBoot2Properties properties;
        private final ObjectProvider<HttpRequestInterceptor> interceptorProvider;

        /**
         * @param properties           ??
         * @param interceptorProvider  ??????????? WebMvcConfigurer ????
         */
        MvcTracingConfiguration(InsightBoot2Properties properties,
                                ObjectProvider<HttpRequestInterceptor> interceptorProvider) {
            this.properties = properties;
            this.interceptorProvider = interceptorProvider;
        }

        /**
         * HTTP ????? Bean?
         *
         * @param spanReportingListener ????
         * @param props                 ??
         * @return ???
         */
        @Bean
        @ConditionalOnMissingBean
        public HttpRequestInterceptor httpRequestInterceptor(SpanReportingListener spanReportingListener,
                                                             InsightBoot2Properties props) {
            return new HttpRequestInterceptor(spanReportingListener, props);
        }

        /**
         * ?????? Spring MVC?
         *
         * @param registry ??????
         */
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            HttpRequestInterceptor interceptor = interceptorProvider.getIfAvailable();
            if (interceptor != null && properties.isHttpTracingEnabled()) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns(properties.resolveExcludePatterns());
                log.info("[Boot2??] HTTP ??????");
            }
        }
    }
}
