package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.instrumentation.HttpRequestInterceptor;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 自动配置类
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(InsightProperties.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightAutoConfiguration implements WebMvcConfigurer {

    private final InsightProperties properties;

    public InsightAutoConfiguration(InsightProperties properties) {
        this.properties = properties;
        properties.validate(); // 验证配置

        log.info("[自动配置] 开始初始化 Spring Insight Agent");
        log.info("[自动配置] 服务配置: name={}, instance={}, collector={}",
                properties.getServiceName(),
                properties.getServiceInstance(),
                properties.getCollector().getUrl());
    }

    /**
     * 异步上报器 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public AsyncSpanReporter asyncSpanReporter() {
        String serviceInstance = properties.getServiceInstance();
        if (serviceInstance == null || serviceInstance.trim().isEmpty()) {
            // 自动生成服务实例标识：host:port
            serviceInstance = "localhost:" + getServerPort();
        }

        AsyncSpanReporter reporter = new AsyncSpanReporter(
                properties.getCollector().getUrl(),
                properties.getServiceName(),
                serviceInstance
        );

        // 启动上报器
        reporter.start();
        log.info("[自动配置] 异步上报器初始化完成");

        return reporter;
    }

    /**
     * Span 报告监听器
     */
    @Bean
    @ConditionalOnMissingBean
    public SpanReportingListener spanReportingListener(AsyncSpanReporter reporter) {
        log.info("[自动配置] Span报告监听器初始化完成");
        return new SpanReportingListener(reporter);
    }

    /**
     * HTTP 请求拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.insight", name = "http-tracing-enabled", havingValue = "true", matchIfMissing = true)
    public HttpRequestInterceptor httpRequestInterceptor() {
        log.info("[自动配置] HTTP请求拦截器初始化完成");
        return new HttpRequestInterceptor();
    }

    /**
     * 注册拦截器到 Spring MVC
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (properties.isHttpTracingEnabled()) {
            registry.addInterceptor(httpRequestInterceptor())
                    .addPathPatterns("/**")
                    .excludePathPatterns(properties.getExcludePatterns());
            log.info("[自动配置] HTTP拦截器已注册，排除路径: {}", (Object) properties.getExcludePatterns());
        }
    }

    /**
     * 获取服务器端口（简化实现）
     */
    private String getServerPort() {
        try {
            // 从系统属性或环境变量获取
            String port = System.getProperty("server.port", "8080");
            if ("0".equals(port)) {
                // 随机端口
                port = "8080";
            }
            return port;
        } catch (Exception e) {
            log.warn("[自动配置] 获取服务器端口失败，使用默认端口8080", e);
            return "8080";
        }
    }

    /**
     * 应用关闭时清理资源
     */
    @Bean
    public ShutdownHook shutdownHook(AsyncSpanReporter reporter) {
        return new ShutdownHook(reporter);
    }

    /**
     * 关闭钩子
     */
    private static class ShutdownHook {
        private final AsyncSpanReporter reporter;

        public ShutdownHook(AsyncSpanReporter reporter) {
            this.reporter = reporter;
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        }

        private void shutdown() {
            log.info("[关闭钩子] 正在停止 Spring Insight Agent...");
            if (reporter != null) {
                reporter.stop();
            }
            log.info("[关闭钩子] Spring Insight Agent 已停止");
        }
    }
}
