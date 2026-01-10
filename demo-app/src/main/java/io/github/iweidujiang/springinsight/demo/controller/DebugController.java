package io.github.iweidujiang.springinsight.demo.controller;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 验证测试
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/10
 * └───────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/debug")
public class DebugController {

    private final InsightProperties insightProperties;
    private final ApplicationContext applicationContext;

    public DebugController(InsightProperties insightProperties, ApplicationContext applicationContext) {
        this.insightProperties = insightProperties;
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        log.info("=== Spring Insight 调试信息 ===");
        log.info("InsightProperties Bean 是否存在: {}", insightProperties != null);

        // 检查所有包含 "insight" 的 Bean
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            if (beanName.toLowerCase().contains("insight")) {
                log.info("找到相关 Bean: {}", beanName);
            }
        }

        // 检查自动配置类
        try {
            Class<?> autoConfigClass = Class.forName("io.github.iweidujiang.springinsight.agent.autoconfigure.InsightAutoConfiguration");
            log.info("InsightAutoConfiguration 类可加载: true");
        } catch (ClassNotFoundException e) {
            log.info("InsightAutoConfiguration 类可加载: false");
        }
    }

    @GetMapping("/insight")
    public Map<String, Object> getInsightStatus() {
        boolean configLoaded = insightProperties != null;
        boolean autoConfigClassExists = false;

        try {
            Class.forName("io.github.iweidujiang.springinsight.agent.autoconfigure.InsightAutoConfiguration");
            autoConfigClassExists = true;
        } catch (ClassNotFoundException e) {
            // 忽略
        }

        return Map.of(
                "configLoaded", configLoaded,
                "autoConfigClassExists", autoConfigClassExists,
                "configProperties", configLoaded ? Map.of(
                        "serviceName", insightProperties.getServiceName(),
                        "collectorUrl", insightProperties.getCollector().getUrl(),
                        "enabled", insightProperties.isEnabled()
                ) : "null"
        );
    }
}
