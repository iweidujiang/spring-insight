package io.github.iweidujiang.springinsight.agent;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightBeanConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 本模块自检用最小启动类（不拉取 starter 中的 collector 扫描）。业务工程请使用 {@code @EnableSpringInsight} + starter。
 */
@SpringBootApplication
@Import(InsightBeanConfiguration.class)
public class InsightAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsightAgentApplication.class, args);
    }
}
