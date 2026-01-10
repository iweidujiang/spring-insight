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

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 专门负责创建 Spring Insight 核心 Bean 的配置类
 * |    此配置类不实现 WebMvcConfigurer，避免与 MVC 生命周期产生循环依赖
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/10
 * └───────────────────────────────────────────────
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(InsightProperties.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightBeanConfiguration {

    private final InsightProperties properties;

    public InsightBeanConfiguration(InsightProperties properties) {
        this.properties = properties;
        properties.validate();
        log.info("[Bean配置] 开始初始化 Spring Insight 核心组件");
    }

    /**
     * 异步上报器 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public AsyncSpanReporter asyncSpanReporter() {
        String serviceInstance = properties.getServiceInstance();
        if (serviceInstance == null || serviceInstance.trim().isEmpty()) {
            serviceInstance = "localhost:" + getServerPort();
        }

        AsyncSpanReporter reporter = new AsyncSpanReporter(
                properties.getCollector().getUrl(),
                properties.getServiceName(),
                serviceInstance
        );
        reporter.start();
        log.info("[Bean配置] 异步上报器初始化完成");
        return reporter;
    }

    /**
     * Span 报告监听器 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public SpanReportingListener spanReportingListener(AsyncSpanReporter asyncSpanReporter) {
        log.info("[Bean配置] Span报告监听器初始化完成");
        return new SpanReportingListener(asyncSpanReporter);
    }

    /**
     * HTTP 请求拦截器 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.insight", name = "http-tracing-enabled", havingValue = "true", matchIfMissing = true)
    public HttpRequestInterceptor httpRequestInterceptor(SpanReportingListener spanReportingListener) {
        log.info("[Bean配置] HTTP请求拦截器初始化完成");
        return new HttpRequestInterceptor(spanReportingListener);
    }

    /**
     * 获取服务器端口（简化实现）
     */
    private String getServerPort() {
        try {
            String port = System.getProperty("server.port", "8080");
            return "0".equals(port) ? "8080" : port;
        } catch (Exception e) {
            return "8080";
        }
    }

    /**
     * 应用关闭时清理资源的钩子
     */
    @Bean
    public ShutdownHook shutdownHook(AsyncSpanReporter asyncSpanReporter) {
        log.info("[Bean配置] 注册应用关闭钩子");
        return new ShutdownHook(asyncSpanReporter);
    }

    /**
     * 关闭钩子内部类
     */
    private static class ShutdownHook {
        private final AsyncSpanReporter reporter;
        public ShutdownHook(AsyncSpanReporter reporter) {
            this.reporter = reporter;
            // 注册JVM关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        }
        private void shutdown() {
            log.info("[关闭钩子] 正在停止 Spring Insight Agent 组件...");
            if (reporter != null) {
                reporter.stop(); // 调用上报器的停止方法， flush剩余数据
            }
            log.info("[关闭钩子] Spring Insight Agent 组件已停止");
        }
    }
}
