package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.instrumentation.HttpRequestInterceptor;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 自动配置类
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(InsightProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightAutoConfiguration implements WebMvcConfigurer {

    private final InsightProperties properties;

    /** 延迟解析，避免与本类 {@link Bean} 方法同名的拦截器形成构造/字段注入环（见 Spring MVC 对 WebMvcConfigurer 的装配顺序）。 */
    private final ObjectProvider<HttpRequestInterceptor> httpRequestInterceptorProvider;

    public InsightAutoConfiguration(InsightProperties properties,
                                    ObjectProvider<HttpRequestInterceptor> httpRequestInterceptorProvider) {
        this.properties = properties;
        this.httpRequestInterceptorProvider = httpRequestInterceptorProvider;
        log.info("[MVC配置] Spring Insight MVC 配置准备就绪");
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.insight", name = "http-tracing-enabled", havingValue = "true", matchIfMissing = true)
    public HttpRequestInterceptor httpRequestInterceptor(SpanReportingListener spanReportingListener) {
        log.info("[MVC配置] HTTP 请求拦截器 Bean 已创建");
        return new HttpRequestInterceptor(spanReportingListener, properties);
    }

    /**
     * 注册拦截器到 Spring MVC
     * 此方法在 Spring MVC 生命周期中被调用，此时所有 Bean 都已就绪。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        HttpRequestInterceptor interceptor = httpRequestInterceptorProvider.getIfAvailable();
        if (properties.isHttpTracingEnabled() && interceptor != null) {
            registry.addInterceptor(interceptor)
                    .addPathPatterns("/**")
                    .excludePathPatterns(properties.getExcludePatterns());
            log.info("[MVC配置] HTTP拦截器已成功注册，排除路径: {}", (Object) properties.getExcludePatterns());
        } else if (properties.isHttpTracingEnabled()) {
            log.warn("[MVC配置] HTTP追踪已启用，但 HttpRequestInterceptor Bean 未找到。请检查配置。");
        }
    }
}
