package io.github.iweidujiang.springinsight.agent.autoconfigure;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code @EnableSpringInsight} 触发的延迟导入选择器。
 * <p>
 * 按注解 {@code enabled} 与 Web 栈条件导入 Agent 配置；
 * 若 classpath 存在 all-in-one Starter 的 {@code SpringInsightAutoConfiguration} 则一并导入（兼容旧用法）。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/1/17
 */
public class InsightAutoConfigurationImportSelector implements DeferredImportSelector, EnvironmentAware, BeanClassLoaderAware {

    /**
     * Servlet MVC 埋点配置（字符串引用，避免无 webmvc 时类加载即失败）
     */
    private static final String INSIGHT_SERVLET_WEB_CONFIGURATION =
            "io.github.iweidujiang.springinsight.agent.autoconfigure.InsightAutoConfiguration";

    /**
     * all-in-one Starter 自动配置（仅当坐标在 classpath 时导入）
     */
    private static final String SPRING_INSIGHT_ALL_IN_ONE =
            "io.github.iweidujiang.springinsight.config.SpringInsightAutoConfiguration";

    /** Environment：读取 web-application-type */
    private Environment environment;

    /** Bean ClassLoader */
    private ClassLoader beanClassLoader;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    /**
     * 根据 {@code @EnableSpringInsight} 属性选择导入项。
     *
     * @param importingClassMetadata 带注解的配置类元数据
     * @return 配置类全限定名；注解缺失或 enabled=false 时返回空数组
     */
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        Map<String, Object> annotationAttributes =
                importingClassMetadata.getAnnotationAttributes(EnableSpringInsight.class.getName());
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

        // 仅当业务仍依赖 all-in-one starter 时导入；纯 agent-starter 场景跳过
        if (InsightWebStackDetect.isPresent(SPRING_INSIGHT_ALL_IN_ONE, beanClassLoader)) {
            imports.add(SPRING_INSIGHT_ALL_IN_ONE);
        }
        return imports.toArray(String[]::new);
    }

    /**
     * @return 是否导入 Servlet MVC 埋点配置
     */
    private boolean shouldImportServletWebInsight() {
        String type = environment != null
                ? environment.getProperty("spring.main.web-application-type")
                : null;
        ClassLoader tcl = Thread.currentThread().getContextClassLoader();
        ClassLoader def = ClassUtils.getDefaultClassLoader();
        return InsightWebStackDetect.shouldImportServletWebInsight(type, beanClassLoader, tcl, def);
    }
}
