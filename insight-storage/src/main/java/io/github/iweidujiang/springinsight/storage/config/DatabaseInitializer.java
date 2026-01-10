package io.github.iweidujiang.springinsight.storage.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 数据库初始化逻辑
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/11
 * └───────────────────────────────────────────────
 */
@Slf4j
@Configuration
public class DatabaseInitializer {
    @Bean
    public ApplicationRunner initializeDatabase(DataSource dataSource) {
        return args -> {
            log.info("[存储模块] 检查并初始化数据库表结构...");
            try {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                populator.addScript(new ClassPathResource("sql/schema-h2.sql"));
                populator.setSeparator(";");
                populator.execute(dataSource);
                log.info("[存储模块] 数据库表结构初始化完成");
            } catch (Exception e) {
                log.warn("[存储模块] 数据库初始化可能已完成，或出现错误: {}", e.getMessage());
            }
        };
    }
}
