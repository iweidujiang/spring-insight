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

/**
 * InsightBoot2AutoConfiguration：Boot2 线自动装配入口（spring.factories），含 MVC HTTP 埋点。
 *
 * @since 2026-09-07
 * @author 苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
@Configuration
@EnableConfigurationProperties(InsightBoot2Properties.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightBoot2AutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InsightBoot2AutoConfiguration.class);

    private final InsightBoot2Properties properties;

    /**
     * 解析服务名并打印启动就绪日志。
     *
     * @param properties  Insight 配置
     * @param environment Spring Environment
     */
    public InsightBoot2AutoConfiguration(InsightBoot2Properties properties, Environment environment) {
        this.properties = properties;
        properties.resolveServiceNameFromEnvironment(environment);
        properties.validate();
        log.info("[Boot2配置] Spring Insight Boot2 Agent 已就绪: serviceName={}, serverUrl={}",
                properties.getServiceName(),
                properties.hasServerUrl() ? properties.normalizeServerUrl() : "(未配置)");
    }

    /**
     * HTTP 批量上报 Sink（配置了 server-url 时启用）。
     *
     * @param objectMapper Jackson
     * @return Sink
     */
    @Bean
    @ConditionalOnMissingBean(InsightBatchSink.class)
    @ConditionalOnProperty(prefix = "spring.insight", name = "server-url")
    public InsightBatchSink httpInsightBatchSink(ObjectMapper objectMapper) {
        if (!StringUtils.hasText(properties.normalizeServerUrl())) {
            throw new IllegalStateException("spring.insight.server-url 不能为空");
        }
        return new HttpInsightBatchSink(properties, objectMapper);
    }

    /**
     * 异步 Span 上报器。
     *
     * @param batchSinkProvider Sink Provider
     * @return 已 start 的上报器
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    public AsyncSpanReporter asyncSpanReporter(ObjectProvider<InsightBatchSink> batchSinkProvider) {
        AsyncSpanReporter reporter = new AsyncSpanReporter(properties.getServiceName(), batchSinkProvider);
        reporter.start();
        return reporter;
    }

    /**
     * Span 上报门面。
     *
     * @param asyncSpanReporter 异步上报器
     * @return 监听器
     */
    @Bean
    @ConditionalOnMissingBean
    public SpanReportingListener spanReportingListener(AsyncSpanReporter asyncSpanReporter,
                                                       ObjectProvider<io.github.iweidujiang.springinsight.agent.boot2.micrometer.InsightMicrometerBridge> micrometerBridge) {
        return new SpanReportingListener(asyncSpanReporter, micrometerBridge);
    }

    /**
     * MVC HTTP 埋点（仅 Servlet Web 应用）。
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
         * @param properties          配置
         * @param interceptorProvider 拦截器（避免 WebMvcConfigurer 循环依赖）
         */
        MvcTracingConfiguration(InsightBoot2Properties properties,
                                ObjectProvider<HttpRequestInterceptor> interceptorProvider) {
            this.properties = properties;
            this.interceptorProvider = interceptorProvider;
        }

        /**
         * HTTP 追踪拦截器 Bean。
         *
         * @param spanReportingListener 上报入口
         * @param props                 配置
         * @return 拦截器
         */
        @Bean
        @ConditionalOnMissingBean
        public HttpRequestInterceptor httpRequestInterceptor(SpanReportingListener spanReportingListener,
                                                             InsightBoot2Properties props) {
            return new HttpRequestInterceptor(spanReportingListener, props);
        }

        /**
         * 注册拦截器到 Spring MVC。
         *
         * @param registry 拦截器注册表
         */
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            HttpRequestInterceptor interceptor = interceptorProvider.getIfAvailable();
            if (interceptor != null && properties.isHttpTracingEnabled()) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns(properties.resolveExcludePatterns());
                log.info("[Boot2-MVC] HTTP 追踪拦截器已注册");
            }
        }
    }
}
