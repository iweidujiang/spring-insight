package io.github.iweidujiang.springinsight.config;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将 classpath:/static/ 映射到 {@code spring.insight.ui-base-path} 下，使打包后的 SPA（带 base 前缀）可访问。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
@RequiredArgsConstructor
public class InsightUiWebMvcConfiguration implements WebMvcConfigurer {

    private final InsightProperties insightProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String base = insightProperties.normalizeUiBasePath();
        if (base.isEmpty()) {
            return;
        }
        registry.addResourceHandler(base + "/**")
                .addResourceLocations("classpath:/static/");
    }
}
