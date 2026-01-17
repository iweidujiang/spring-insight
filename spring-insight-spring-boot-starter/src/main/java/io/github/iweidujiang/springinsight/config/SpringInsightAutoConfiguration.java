package io.github.iweidujiang.springinsight.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 自动配置类
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │ 📅 @since 2026/1/17
 * └───────────────────────────────────────────────
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SpringInsightProperties.class)
public class SpringInsightAutoConfiguration {

    @Autowired
    private SpringInsightProperties properties;

    /**
     * 配置数据源
     */
    @Bean
    @ConditionalOnMissingBean
    public DataSource dataSource() {
        log.info("📦 正在初始化 Spring Insight 数据源");
        
        // 确保数据库存在
        createDatabaseIfNotExists();
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setJdbcUrl(properties.getDatasource().getUrl());
        hikariConfig.setUsername(properties.getDatasource().getUsername());
        hikariConfig.setPassword(properties.getDatasource().getPassword());
        
        // HikariCP 连接池配置
        hikariConfig.setPoolName("SpringInsightHikariCP");
        hikariConfig.setMinimumIdle(5);
        hikariConfig.setMaximumPoolSize(20);
        hikariConfig.setIdleTimeout(300000);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setAutoCommit(true);
        
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);
        
        // 初始化数据库表
        initDatabaseSchema(dataSource);
        
        return dataSource;
    }
    
    /**
     * 创建数据库（如果不存在）
     */
    private void createDatabaseIfNotExists() {
        String jdbcUrl = properties.getDatasource().getUrl();
        String username = properties.getDatasource().getUsername();
        String password = properties.getDatasource().getPassword();
        
        // 提取数据库名
        String databaseName = extractDatabaseName(jdbcUrl);
        if (databaseName == null) {
            log.warn("⚠️ 无法提取数据库名，跳过自动创建数据库");
            return;
        }
        
        // 创建不带数据库名的连接URL
        String connectionUrl = jdbcUrl.substring(0, jdbcUrl.indexOf(databaseName));
        
        try {
            Connection conn = DriverManager.getConnection(connectionUrl, username, password);
            conn.createStatement().execute("CREATE DATABASE IF NOT EXISTS `" + databaseName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            log.info("✅ 数据库 '{}' 已创建或已存在", databaseName);
            conn.close();
        } catch (SQLException e) {
            log.error("❌ 创建数据库失败: {}", e.getMessage());
        }
    }
    
    /**
     * 从JDBC URL中提取数据库名
     */
    private String extractDatabaseName(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        
        // 处理 MySQL 连接URL格式
        if (jdbcUrl.startsWith("jdbc:mysql://")) {
            // 去掉jdbc:mysql://前缀
            String url = jdbcUrl.substring(13);
            // 提取主机和端口部分
            int slashIndex = url.indexOf("/");
            if (slashIndex == -1) {
                return null;
            }
            // 提取数据库名部分
            String dbPart = url.substring(slashIndex + 1);
            // 去掉可能的参数
            int paramIndex = dbPart.indexOf("?");
            if (paramIndex != -1) {
                dbPart = dbPart.substring(0, paramIndex);
            }
            return dbPart;
        }
        
        return null;
    }
    
    /**
     * 初始化数据库表
     */
    private void initDatabaseSchema(DataSource dataSource) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            
            // 读取schema文件
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            populator.addScripts(
                    resolver.getResource("classpath:sql/schema-mysql.sql")
            );
            
            // 设置执行脚本的分隔符和编码
            populator.setSeparator(";");
            populator.setSqlScriptEncoding("UTF-8");
            populator.setContinueOnError(true);
            
            // 执行脚本
            DatabasePopulatorUtils.execute(populator, dataSource);
            log.info("✅ 数据库表初始化完成");
        } catch (Exception e) {
            log.error("❌ 数据库表初始化失败: {}", e.getMessage());
        }
    }
    
    /**
     * 配置SqlSessionFactory
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        
        // 设置Mapper位置
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        factoryBean.setMapperLocations(resolver.getResources("classpath*:mapper/**/*.xml"));
        
        return factoryBean.getObject();
    }
    
    /**
     * 配置SqlSessionTemplate
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
    
    /**
     * 配置事务管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}