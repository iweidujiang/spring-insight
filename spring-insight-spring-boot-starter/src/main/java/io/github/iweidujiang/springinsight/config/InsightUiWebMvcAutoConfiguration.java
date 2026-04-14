package io.github.iweidujiang.springinsight.config;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将 {@code classpath:/static/} 映射到 {@code spring.insight.ui-base-path} 下。
 * <p>
 * 必须单独成条 {@link AutoConfiguration} 且本类<strong>不</strong>实现 {@link WebMvcConfigurer}：
 * 若由 {@link SpringInsightAutoConfiguration} {@code @Import} 带 {@code implements WebMvcConfigurer} 的配置类，
 * 在 Spring Cloud Gateway（无 spring-webmvc）进程里<strong>加载该类字节码即会解析接口</strong>，导致
 * {@code ClassNotFoundException: WebMvcConfigurer}。此处仅在 {@code @ConditionalOnClass} 通过后才实例化本类及匿名 {@link WebMvcConfigurer}。
 */
@AutoConfiguration(after = SpringInsightAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
public class InsightUiWebMvcAutoConfiguration {

    @Bean
    public WebMvcConfigurer springInsightUiResourceConfigurer(InsightProperties insightProperties) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                String base = insightProperties.normalizeUiBasePath();
                if (!base.isEmpty()) {
                    registry.addResourceHandler(base + "/**")
                            .addResourceLocations("classpath:/static/");
                }
            }
        };
    }
}
