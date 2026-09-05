package io.github.iweidujiang.springinsight.agent.autoconfigure;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent Starter 用延迟导入选择器：按 Web 栈条件装配埋点，不拉取 Server/UI。
 * <p>
 * 与 {@link InsightAutoConfigurationImportSelector} 的差异：
 * <ul>
 *   <li>不依赖 {@code @EnableSpringInsight} 注解属性（由 AutoConfiguration 触发）</li>
 *   <li>永不导入 all-in-one 的 {@code SpringInsightAutoConfiguration}</li>
 * </ul>
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/8/16
 */
public class InsightAgentAutoConfigurationImportSelector
        implements DeferredImportSelector, EnvironmentAware, BeanClassLoaderAware {

    /**
     * Servlet MVC 埋点配置类名（字符串引用，避免 Gateway 上提前解析 {@code WebMvcConfigurer}）
     */
    private static final String INSIGHT_SERVLET_WEB_CONFIGURATION =
            "io.github.iweidujiang.springinsight.agent.autoconfigure.InsightAutoConfiguration";

    /** Spring Environment：读取 {@code spring.main.web-application-type} */
    private Environment environment;

    /** Bean ClassLoader：探测 Gateway / 可选模块 */
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
     * 选择要导入的配置类。
     *
     * @param importingClassMetadata 触发导入的配置类元数据（本选择器不读注解属性）
     * @return 配置类全限定名数组
     */
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        List<String> imports = new ArrayList<>();
        // 核心 Bean：异步上报、监听器、JVM、DB 切面、Http Sink 等
        imports.add(InsightBeanConfiguration.class.getName());
        imports.add(InsightAsyncAutoConfiguration.class.getName());
        if (shouldImportServlet()) {
            imports.add(INSIGHT_SERVLET_WEB_CONFIGURATION);
        }
        // WebFlux 配置类自身带 @ConditionalOnWebApplication(REACTIVE)，Servlet 栈上不会生效
        imports.add(InsightWebFluxAutoConfiguration.class.getName());
        if (ClassUtils.isPresent("org.springframework.cloud.gateway.filter.GlobalFilter",
                beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader())) {
            imports.add(InsightGatewayAutoConfiguration.class.getName());
        }
        if (ClassUtils.isPresent("org.springframework.web.reactive.function.client.WebClient",
                beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader())) {
            imports.add(InsightWebClientAutoConfiguration.class.getName());
        }
        return imports.toArray(String[]::new);
    }

    /**
     * @return 是否导入 Servlet MVC 埋点
     */
    private boolean shouldImportServlet() {
        String type = environment != null
                ? environment.getProperty("spring.main.web-application-type")
                : null;
        ClassLoader tcl = Thread.currentThread().getContextClassLoader();
        ClassLoader def = ClassUtils.getDefaultClassLoader();
        return InsightWebStackDetect.shouldImportServletWebInsight(type, beanClassLoader, tcl, def);
    }
}
