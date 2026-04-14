package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.collector.JvmMetricsCollector;
import io.github.iweidujiang.springinsight.agent.collector.JvmMetricsReporter;
import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.instrumentation.DbCallAspect;
import io.github.iweidujiang.springinsight.agent.instrumentation.HttpRequestInterceptor;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.sink.InsightBatchSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ApplicationRunner;
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
@EnableConfigurationProperties({InsightProperties.class, InsightJvmMetricsProperties.class})
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightBeanConfiguration {

    private final InsightProperties properties;
    private final InsightJvmMetricsProperties jvmMetricsProperties;

    public InsightBeanConfiguration(InsightProperties properties, InsightJvmMetricsProperties jvmMetricsProperties) {
        this.properties = properties;
        this.jvmMetricsProperties = jvmMetricsProperties;
        properties.validate();
        log.info("[Bean配置] 开始初始化 Spring Insight 核心组件");
    }

    /**
     * 异步上报器 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public AsyncSpanReporter asyncSpanReporter(ObjectProvider<InsightBatchSink> batchSinkProvider) {
        String serviceInstance = properties.getServiceInstance();
        if (serviceInstance == null || serviceInstance.trim().isEmpty()) {
            serviceInstance = "localhost:" + getServerPort();
        }

        AsyncSpanReporter reporter = new AsyncSpanReporter(
                properties.getServiceName(),
                serviceInstance,
                batchSinkProvider
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
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "spring.insight", name = "http-tracing-enabled", havingValue = "true", matchIfMissing = true)
    public HttpRequestInterceptor httpRequestInterceptor(SpanReportingListener spanReportingListener) {
        log.info("[Bean配置] HTTP请求拦截器初始化完成");
        return new HttpRequestInterceptor(spanReportingListener, properties);
    }

    /**
     * 同步诊断日志开关到 TraceContext（静态上下文）
     */
    @Bean
    public ApplicationRunner insightTraceContextDiagnosticSync() {
        return args -> {
            TraceContext.setDiagnosticLogs(properties.isDiagnosticLogs());
            log.info("[Bean配置] TraceContext 诊断日志: {}", properties.isDiagnosticLogs());
        };
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
     * JVM指标收集器 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.insight.jvm-metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JvmMetricsCollector jvmMetricsCollector() {
        String serviceInstance = properties.getServiceInstance();
        if (serviceInstance == null || serviceInstance.trim().isEmpty()) {
            serviceInstance = "localhost:" + getServerPort();
        }
        
        Integer hostPort = null;
        try {
            hostPort = Integer.parseInt(getServerPort());
        } catch (NumberFormatException e) {
            log.warn("[Bean配置] 无法解析服务器端口，使用默认值: 8080");
            hostPort = 8080;
        }
        
        JvmMetricsCollector collector = new JvmMetricsCollector(
                properties.getServiceName(),
                serviceInstance,
                hostPort
        );
        log.info("[Bean配置] JVM指标收集器初始化完成");
        return collector;
    }
    
    /**
     * JVM指标报告器 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.insight.jvm-metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JvmMetricsReporter jvmMetricsReporter(JvmMetricsCollector jvmMetricsCollector, AsyncSpanReporter asyncSpanReporter) {
        JvmMetricsReporter reporter = new JvmMetricsReporter(
                jvmMetricsCollector,
                asyncSpanReporter,
                jvmMetricsProperties.getReportInterval()
        );
        log.info("[Bean配置] JVM指标报告器初始化完成，上报间隔: {}ms", jvmMetricsProperties.getReportInterval());
        return reporter;
    }
    
    /**
     * 数据库调用切面 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.insight.db-metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DbCallAspect dbCallAspect(SpanReportingListener spanReportingListener) {
        String serviceInstance = properties.getServiceInstance();
        if (serviceInstance == null || serviceInstance.trim().isEmpty()) {
            serviceInstance = "localhost:" + getServerPort();
        }
        
        String hostIp = "127.0.0.1";
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            hostIp = localHost.getHostAddress();
        } catch (java.net.UnknownHostException e) {
            log.warn("[Bean配置] 无法获取主机IP，使用默认值: 127.0.0.1");
        }
        
        Integer hostPort = null;
        try {
            hostPort = Integer.parseInt(getServerPort());
        } catch (NumberFormatException e) {
            log.warn("[Bean配置] 无法解析服务器端口，使用默认值: 8080");
            hostPort = 8080;
        }
        
        DbCallAspect dbCallAspect = new DbCallAspect(
                spanReportingListener,
                properties.getServiceName(),
                serviceInstance,
                hostIp,
                hostPort
        );
        log.info("[Bean配置] 数据库调用切面初始化完成");
        return dbCallAspect;
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
