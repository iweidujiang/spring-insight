package io.github.iweidujiang.springinsight.server.security;

import io.github.iweidujiang.springinsight.server.config.InsightServerSecurityProperties;
import jakarta.servlet.Filter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 注册可选鉴权 Filter 与会话表（不引入 Spring Security，默认无感）。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Configuration
@EnableConfigurationProperties(InsightServerSecurityProperties.class)
public class InsightServerSecurityConfiguration {

    /**
     * @param properties 安全配置
     * @return UI 会话存储
     */
    @Bean
    public UiSessionStore uiSessionStore(InsightServerSecurityProperties properties) {
        return new UiSessionStore(properties);
    }

    /**
     * @param properties 安全配置
     * @return 上报 Token Filter 注册
     */
    @Bean
    public FilterRegistrationBean<Filter> ingestTokenAuthFilter(InsightServerSecurityProperties properties) {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new IngestTokenAuthFilter(properties));
        bean.addUrlPatterns("/api/v1/spans/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        bean.setName("insightIngestTokenAuthFilter");
        return bean;
    }

    /**
     * @param properties   安全配置
     * @param sessionStore 会话表
     * @return UI 鉴权 Filter 注册
     */
    @Bean
    public FilterRegistrationBean<Filter> uiAuthFilter(InsightServerSecurityProperties properties,
                                                       UiSessionStore sessionStore) {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new UiAuthFilter(properties, sessionStore));
        bean.addUrlPatterns("/api/v1/ui/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        bean.setName("insightUiAuthFilter");
        return bean;
    }
}
