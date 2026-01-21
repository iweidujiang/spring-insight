package io.github.iweidujiang.springinsight.agent.autoconfigure;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 启用注解
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/17
 * └───────────────────────────────────────────────
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
     * 服务名称
     */
    String serviceName() default "";
    
    /**
     * 服务实例标识
     */
    String serviceInstance() default "";
    
    /**
     * 采样率（0.0 - 1.0，1.0表示采样所有请求）
     */
    double sampleRate() default 1.0;
    
    /**
     * 是否启用 HTTP 请求追踪
     */
    boolean httpTracingEnabled() default true;
    
    /**
     * 是否启用 JVM 指标监控
     */
    boolean jvmMetricsEnabled() default true;
    
    /**
     * 是否启用数据库调用监控
     */
    boolean dbMetricsEnabled() default true;
}