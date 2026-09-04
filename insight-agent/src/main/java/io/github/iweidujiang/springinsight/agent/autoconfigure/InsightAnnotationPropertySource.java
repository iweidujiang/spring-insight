package io.github.iweidujiang.springinsight.agent.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * 将主类上 {@link EnableSpringInsight} 的属性桥接到 Environment。
 * <p>
 * 使用 {@code addLast}，保证 {@code application.yml} 仍可覆盖注解值
 * （文档约定：配置文件 &gt; 注解 &gt; 默认值）。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/1/17
 */
public class InsightAnnotationPropertySource implements EnvironmentPostProcessor {

    /** PropertySource 名称，便于调试 Environment */
    private static final String PROPERTY_SOURCE_NAME = "springInsightAnnotation";

    /**
     * 扫描主类注解并写入低优先级属性源。
     *
     * @param environment 可配置环境
     * @param application SpringApplication（用于取 mainClass）
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Class<?> mainClass = application.getMainApplicationClass();
        if (mainClass == null) {
            mainClass = getMainClassFromStackTrace();
        }
        if (mainClass == null) {
            return;
        }

        EnableSpringInsight annotation = mainClass.getAnnotation(EnableSpringInsight.class);
        if (annotation == null) {
            return;
        }

        Properties properties = new Properties();
        // 总开关
        properties.setProperty("spring.insight.enabled", String.valueOf(annotation.enabled()));

        if (StringUtils.hasText(annotation.serviceName())) {
            properties.setProperty("spring.insight.service-name", annotation.serviceName());
        }
        if (StringUtils.hasText(annotation.serviceInstance())) {
            properties.setProperty("spring.insight.service-instance", annotation.serviceInstance());
        }
        properties.setProperty("spring.insight.sample-rate", String.valueOf(annotation.sampleRate()));
        properties.setProperty("spring.insight.http-tracing-enabled",
                String.valueOf(annotation.httpTracingEnabled()));
        // JVM / DB 开关与 @ConditionalOnProperty 前缀对齐
        properties.setProperty("spring.insight.jvm-metrics.enabled",
                String.valueOf(annotation.jvmMetricsEnabled()));
        properties.setProperty("spring.insight.db-metrics.enabled",
                String.valueOf(annotation.dbMetricsEnabled()));

        // addLast：yaml 中同名键优先
        environment.getPropertySources().addLast(new PropertiesPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    /**
     * 从当前线程堆栈中查找 {@code main} 方法所在类（兜底）。
     *
     * @return 主类或 {@code null}
     */
    private Class<?> getMainClassFromStackTrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if ("main".equals(element.getMethodName())) {
                try {
                    return Class.forName(element.getClassName());
                } catch (ClassNotFoundException ignored) {
                    // 继续向上找
                }
            }
        }
        return null;
    }
}
