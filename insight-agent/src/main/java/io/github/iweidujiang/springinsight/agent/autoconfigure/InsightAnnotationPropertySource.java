package io.github.iweidujiang.springinsight.agent.autoconfigure;

import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;

import java.lang.annotation.Annotation;
import java.util.Properties;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 注解属性源
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/17
 * └───────────────────────────────────────────────
 */
public class InsightAnnotationPropertySource implements EnvironmentPostProcessor {
    
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, org.springframework.boot.SpringApplication application) {
        // 获取主应用类
        Class<?> mainClass = application.getMainApplicationClass();
        if (mainClass == null) {
            // 尝试从线程堆栈中获取主类
            mainClass = getMainClassFromStackTrace();
        }
        
        if (mainClass != null) {
            // 检查主类是否使用了@EnableSpringInsight注解
            EnableSpringInsight annotation = mainClass.getAnnotation(EnableSpringInsight.class);
            if (annotation != null) {
                // 将注解属性转换为Properties
                Properties properties = new Properties();
                
                // 服务名称
                if (!annotation.serviceName().isEmpty()) {
                    properties.setProperty("spring.insight.service-name", annotation.serviceName());
                }
                
                // 服务实例标识
                if (!annotation.serviceInstance().isEmpty()) {
                    properties.setProperty("spring.insight.service-instance", annotation.serviceInstance());
                }
                
                // 采样率
                properties.setProperty("spring.insight.sample-rate", String.valueOf(annotation.sampleRate()));
                
                // HTTP请求追踪开关
                properties.setProperty("spring.insight.http-tracing-enabled", String.valueOf(annotation.httpTracingEnabled()));
                
                // Collector服务URL
                if (!annotation.collectorUrl().isEmpty()) {
                    properties.setProperty("spring.insight.collector.url", annotation.collectorUrl());
                }
                
                // 添加到环境中
                environment.getPropertySources().addFirst(new PropertiesPropertySource("springInsightAnnotation", properties));
            }
        }
    }
    
    /**
     * 从线程堆栈中获取主类
     */
    private Class<?> getMainClassFromStackTrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if ("main".equals(element.getMethodName())) {
                try {
                    return Class.forName(element.getClassName());
                } catch (ClassNotFoundException e) {
                    // 忽略，继续查找
                }
            }
        }
        return null;
    }
}