package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.collector.JvmMetricsCollector;
import io.github.iweidujiang.springinsight.agent.collector.JvmMetricsReporter;
import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.instrumentation.DbCallAspect;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.sink.HttpInsightBatchSink;
import io.github.iweidujiang.springinsight.agent.sink.InsightBatchSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Spring Insight 核心 Bean 配置（不含 WebMvcConfigurer，避免与 MVC 循环依赖）。
 * <p>
 * 由 Agent Starter 自动导入或 {@code @EnableSpringInsight} 导入；业务侧使用 agent-starter 时
 * <strong>不必</strong>再写注解（注解仅作可选显式开关 / 属性覆盖）。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/1/10
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({InsightProperties.class, InsightJvmMetricsProperties.class})
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightBeanConfiguration {

    /** Insight 主配置（可能已由 yaml / 注解填充） */
    private final InsightProperties properties;

    /** JVM 指标子配置 */
    private final InsightJvmMetricsProperties jvmMetricsProperties;

    /**
     * 注入配置并解析服务名，再做校验。
     *
     * @param properties            {@code spring.insight.*}
     * @param jvmMetricsProperties  {@code spring.insight.jvm-metrics.*}
     * @param environment           用于读取 {@code spring.application.name}
     */
    public InsightBeanConfiguration(InsightProperties properties,
                                    InsightJvmMetricsProperties jvmMetricsProperties,
                                    Environment environment) {
        this.properties = properties;
        this.jvmMetricsProperties = jvmMetricsProperties;
        // service-name 空时回退到 spring.application.name
        properties.resolveServiceNameFromEnvironment(environment);
        properties.validate();
        log.info("[Bean配置] 开始初始化 Spring Insight 核心组件: serviceName={}, serverUrl={}",
                properties.getServiceName(),
                properties.hasServerUrl() ? properties.normalizeServerUrl() : "(embedded/none)");
    }

    /**
     * HTTP 批量上报 Sink：仅当配置了 {@code spring.insight.server-url} 时启用。
     * <p>
     * 与 embedded 进程内 Sink 互斥：有 server-url 时优先本 Bean；Starter 侧 Local Sink
     * 应在「未配置 server-url」时才注册。
     * </p>
     *
     * @param objectMapper Spring 容器中的 Jackson 映射器
     * @return 指向 Insight Server 的 {@link InsightBatchSink}
     */
    @Bean
    @ConditionalOnMissingBean(InsightBatchSink.class)
    @ConditionalOnProperty(prefix = "spring.insight", name = "server-url")
    public InsightBatchSink httpInsightBatchSink(ObjectMapper objectMapper) {
        if (!StringUtils.hasText(properties.normalizeServerUrl())) {
            throw new IllegalStateException("spring.insight.server-url 已声明但值为空");
        }
        return new HttpInsightBatchSink(properties, objectMapper);
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
