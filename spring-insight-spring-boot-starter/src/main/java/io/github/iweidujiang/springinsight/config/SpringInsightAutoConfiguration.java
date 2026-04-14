package io.github.iweidujiang.springinsight.config;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 自动配置（内存存储，无 JDBC）
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │ 📅 @since 2026/1/17
 * └───────────────────────────────────────────────
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(InsightProperties.class)
@ComponentScan(basePackages = {
        "io.github.iweidujiang.springinsight.collector",
        "io.github.iweidujiang.springinsight.storage",
        "io.github.iweidujiang.springinsight.sink",
        "io.github.iweidujiang.springinsight.controller"
})
public class SpringInsightAutoConfiguration {

    public SpringInsightAutoConfiguration() {
        log.info("[Starter配置] Spring Insight 已启用（链路数据仅内存保留，进程重启即清空）");
    }
}
