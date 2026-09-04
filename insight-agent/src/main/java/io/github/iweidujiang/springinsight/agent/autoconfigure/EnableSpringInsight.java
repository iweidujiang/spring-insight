package io.github.iweidujiang.springinsight.agent.autoconfigure;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可选启用注解：显式打开 Insight 并可用属性覆盖配置。
 * <p>
 * <strong>使用 {@code spring-insight-agent-starter} 时通常不必添加本注解</strong>——
 * Starter 已通过 {@code AutoConfiguration.imports} 自动装配。
 * 仍可保留本注解用于：临时关闭（{@code enabled=false}）、或用注解写 serviceName 等。
 * </p>
 * <p>
 * 配置优先级：{@code application.yml} &gt; 本注解 &gt; {@code spring.application.name}（仅 serviceName 回退）。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/1/17
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(InsightAutoConfigurationImportSelector.class)
public @interface EnableSpringInsight {

    /**
     * 是否启用 Spring Insight
     */
    boolean enabled() default true;

    /**
     * 服务名称；空则回退 {@code spring.insight.service-name} / {@code spring.application.name}
     */
    String serviceName() default "";

    /**
     * 服务实例标识；空则后续用 host:port
     */
    String serviceInstance() default "";

    /**
     * 采样率（0.0 - 1.0）
     */
    double sampleRate() default 1.0;

    /**
     * 是否启用 HTTP 请求追踪
     */
    boolean httpTracingEnabled() default true;

    /**
     * 是否启用 JVM 指标监控（映射到 {@code spring.insight.jvm-metrics.enabled}）
     */
    boolean jvmMetricsEnabled() default true;

    /**
     * 是否启用数据库调用监控（映射到 {@code spring.insight.db-metrics.enabled}）
     */
    boolean dbMetricsEnabled() default true;
}
