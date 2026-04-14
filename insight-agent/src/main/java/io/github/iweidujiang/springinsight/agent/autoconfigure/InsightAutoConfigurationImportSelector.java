package io.github.iweidujiang.springinsight.agent.autoconfigure;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 自动配置导入选择器
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/17
 * └───────────────────────────────────────────────
 */
public class InsightAutoConfigurationImportSelector implements DeferredImportSelector, EnvironmentAware, BeanClassLoaderAware {

    /** 勿使用 {@code InsightAutoConfiguration.class}：会触发类加载并解析 {@code WebMvcConfigurer}。 */
    private static final String INSIGHT_SERVLET_WEB_CONFIGURATION =
            "io.github.iweidujiang.springinsight.agent.autoconfigure.InsightAutoConfiguration";

    private static final String SPRING_CLOUD_GATEWAY_GLOBAL_FILTER =
            "org.springframework.cloud.gateway.filter.GlobalFilter";

    private Environment environment;

    private ClassLoader beanClassLoader;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        Map<String, Object> annotationAttributes = importingClassMetadata.getAnnotationAttributes(EnableSpringInsight.class.getName());
        if (annotationAttributes == null) {
            return new String[0];
        }

        boolean enabled = (Boolean) annotationAttributes.getOrDefault("enabled", true);
        if (!enabled) {
            return new String[0];
        }

        List<String> imports = new ArrayList<>();
        imports.add(InsightBeanConfiguration.class.getName());
        if (shouldImportServletWebInsight()) {
            imports.add(INSIGHT_SERVLET_WEB_CONFIGURATION);
        }
        imports.add(InsightWebFluxAutoConfiguration.class.getName());
        imports.add("io.github.iweidujiang.springinsight.config.SpringInsightAutoConfiguration");
        return imports.toArray(String[]::new);
    }

    /**
     * Servlet 栈上的 {@link InsightAutoConfiguration} 实现了 {@code WebMvcConfigurer}，类加载即解析该接口。
     * Gateway 等 WebFlux 应用常排除 {@code spring-webmvc}，若仍无条件导入该类会触发 {@code ClassNotFoundException}。
     */
    private boolean shouldImportServletWebInsight() {
        if (environment != null) {
            String type = environment.getProperty("spring.main.web-application-type");
            if (type != null && !type.isBlank()) {
                return "servlet".equalsIgnoreCase(type.trim());
            }
        }
        return !isSpringCloudGatewayPresent();
    }

    /**
     * Fat jar / 多层 ClassLoader 下单一路径可能找不到 Gateway，需逐个尝试，避免误判为 Servlet 并导入 MVC 配置。
     */
    private boolean isSpringCloudGatewayPresent() {
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        if (beanClassLoader != null) {
            loaders.add(beanClassLoader);
        }
        ClassLoader tcl = Thread.currentThread().getContextClassLoader();
        if (tcl != null) {
            loaders.add(tcl);
        }
        ClassLoader def = ClassUtils.getDefaultClassLoader();
        if (def != null) {
            loaders.add(def);
        }
        loaders.add(InsightAutoConfigurationImportSelector.class.getClassLoader());
        for (ClassLoader cl : loaders) {
            if (cl == null) {
                continue;
            }
            try {
                Class.forName(SPRING_CLOUD_GATEWAY_GLOBAL_FILTER, false, cl);
                return true;
            } catch (ClassNotFoundException | LinkageError ignored) {
                // try next
            }
        }
        return false;
    }
}
