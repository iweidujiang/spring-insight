package io.github.iweidujiang.springinsight.agent.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 配置属性
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Data
@ConfigurationProperties(prefix = "spring.insight")
public class InsightProperties {

    /**
     * 是否启用 Spring Insight
     */
    private boolean enabled = true;

    /**
     * 服务名称（必填）
     */
    private String serviceName = "test-service"; // 默认值用于测试环境

    /**
     * 服务实例标识（可选，默认使用 host:port）
     */
    private String serviceInstance;

    /**
     * 采样率（0.0 - 1.0，1.0表示采样所有请求）
     */
    private double sampleRate = 1.0;

    /**
     * 是否启用 HTTP 请求追踪
     */
    private boolean httpTracingEnabled = true;

    /**
     * 需要排除的路径模式
     */
    private String[] excludePatterns = { "/actuator/**", "/health", "/prometheus" };

    /**
     * 存储类型：h2 (默认), mysql, postgresql
     */
    private StorageType storageType = StorageType.H2;

    /**
     * 数据源配置
     */
    private Datasource datasource = new Datasource();
    
    /**
     * 服务配置
     */
    private Server server = new Server();
    
    /**
     * 存储类型枚举
     */
    public enum StorageType {
        H2,
        MYSQL,
        POSTGRESQL
    }
    
    /**
     * 数据源配置类
     */
    @Data
    public static class Datasource {
        /**
         * 数据库URL
         */
        private String url = "jdbc:h2:mem:spring_insight;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        
        /**
         * 数据库用户名
         */
        private String username = "sa";
        
        /**
         * 数据库密码
         */
        private String password = "";
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

    /**
     * 验证配置是否有效
     */
    public void validate() {
        if (enabled) {
            if (serviceName == null || serviceName.trim().isEmpty()) {
                throw new IllegalArgumentException("spring.insight.service-name 必须配置");
            }
        }
    }
}
