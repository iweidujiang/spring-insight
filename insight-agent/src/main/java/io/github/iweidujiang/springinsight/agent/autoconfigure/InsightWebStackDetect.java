package io.github.iweidujiang.springinsight.agent.autoconfigure;

import org.springframework.util.ClassUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Servlet / WebFlux / Gateway 栈探测工具，供各类 {@code ImportSelector} 复用。
 * <p>
 * 目的：在 Gateway 等无 spring-webmvc 的环境中，避免加载实现了
 * {@code WebMvcConfigurer} 的配置类导致 {@code ClassNotFoundException}。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/8/16
 */
public final class InsightWebStackDetect {

    /** Spring Cloud Gateway 的标记接口，用于判断是否应跳过 Servlet MVC 埋点配置 */
    private static final String SPRING_CLOUD_GATEWAY_GLOBAL_FILTER =
            "org.springframework.cloud.gateway.filter.GlobalFilter";

    private InsightWebStackDetect() {
    }

    /**
     * 是否应导入 Servlet 侧 {@code InsightAutoConfiguration}（HTTP 拦截器）。
     *
     * @param webApplicationTypeProperty {@code spring.main.web-application-type}，可为 {@code null}
     * @param classLoaders                 用于探测 Gateway 的 ClassLoader 候选（去重后逐个尝试）
     * @return {@code true} 表示可以安全导入 MVC 埋点配置
     */
    public static boolean shouldImportServletWebInsight(String webApplicationTypeProperty,
                                                        ClassLoader... classLoaders) {
        if (webApplicationTypeProperty != null && !webApplicationTypeProperty.isBlank()) {
            return "servlet".equalsIgnoreCase(webApplicationTypeProperty.trim());
        }
        return !isSpringCloudGatewayPresent(classLoaders);
    }

    /**
     * classpath 上是否存在 Spring Cloud Gateway。
     *
     * @param classLoaders 候选 ClassLoader
     * @return 任一加载器能加载到 {@code GlobalFilter} 则为 {@code true}
     */
    public static boolean isSpringCloudGatewayPresent(ClassLoader... classLoaders) {
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        if (classLoaders != null) {
            for (ClassLoader cl : classLoaders) {
                if (cl != null) {
                    loaders.add(cl);
                }
            }
        }
        loaders.add(InsightWebStackDetect.class.getClassLoader());
        for (ClassLoader cl : loaders) {
            try {
                Class.forName(SPRING_CLOUD_GATEWAY_GLOBAL_FILTER, false, cl);
                return true;
            } catch (ClassNotFoundException | LinkageError ignored) {
                // 尝试下一个 ClassLoader
            }
        }
        return false;
    }

    /**
     * 指定全限定名是否在 classpath 上。
     *
     * @param className    全限定类名
     * @param classLoader  优先使用的 ClassLoader，可为 {@code null}
     * @return 可加载则为 {@code true}
     */
    public static boolean isPresent(String className, ClassLoader classLoader) {
        return ClassUtils.isPresent(className, classLoader);
    }
}
