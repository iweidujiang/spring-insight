package io.github.iweidujiang.springinsight.agent.autoconfigure;

import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
public class InsightAutoConfigurationImportSelector implements DeferredImportSelector, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
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
            imports.add(InsightAutoConfiguration.class.getName());
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
        ClassLoader cl = ClassUtils.getDefaultClassLoader();
        if (cl == null) {
            cl = InsightAutoConfigurationImportSelector.class.getClassLoader();
        }
        try {
            Class.forName("org.springframework.cloud.gateway.filter.GlobalFilter", false, cl);
            return false;
        } catch (ClassNotFoundException | LinkageError e) {
            return true;
        }
    }
}
