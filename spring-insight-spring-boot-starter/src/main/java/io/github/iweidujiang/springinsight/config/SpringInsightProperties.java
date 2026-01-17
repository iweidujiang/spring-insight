package io.github.iweidujiang.springinsight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 配置属性类
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │ 📅 @since 2026/1/17
 * └───────────────────────────────────────────────
 */
@Data
@ConfigurationProperties(prefix = "spring.insight")
public class SpringInsightProperties {

    /**
     * 数据源配置
     */
    private Datasource datasource = new Datasource();
    
    /**
     * 服务配置
     */
    private Server server = new Server();
    
    /**
     * 数据源配置类
     */
    @Data
    public static class Datasource {
        /**
         * 数据库URL
         */
        private String url;
        
        /**
         * 数据库用户名
         */
        private String username;
        
        /**
         * 数据库密码
         */
        private String password;
    }
    
    /**
     * 服务配置类
     */
    @Data
    public static class Server {
        /**
         * 服务端口
         */
        private Integer port = 8088;
    }
}