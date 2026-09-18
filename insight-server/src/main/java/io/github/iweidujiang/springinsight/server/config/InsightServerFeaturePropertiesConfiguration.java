package io.github.iweidujiang.springinsight.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 0.3 起新增的 Server 配置属性（Alert / AI；默认关闭）。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Configuration
@EnableConfigurationProperties({
        InsightServerAlertProperties.class,
        InsightServerAiProperties.class
})
public class InsightServerFeaturePropertiesConfiguration {
}
