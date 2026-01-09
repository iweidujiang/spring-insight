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
    private String serviceName;

    /**
     * 服务实例标识（可选，默认使用 host:port）
     */
    private String serviceInstance;

    /**
     * Collector 服务配置
     */
    private Collector collector = new Collector();

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
     * Collector 配置
     */
    @Data
    public static class Collector {
        /**
         * Collector 服务地址（必填）
         */
        private String url = "http://localhost:8080";

        /**
         * 连接超时时间（毫秒）
         */
        private int connectTimeout = 5000;

        /**
         * 读取超时时间（毫秒）
         */
        private int readTimeout = 10000;

        /**
         * 最大重试次数
         */
        private int maxRetries = 3;
    }

    /**
     * 验证配置是否有效
     */
    public void validate() {
        if (enabled) {
            if (serviceName == null || serviceName.trim().isEmpty()) {
                throw new IllegalArgumentException("spring.insight.service-name 必须配置");
            }
            if (collector.url == null || collector.url.trim().isEmpty()) {
                throw new IllegalArgumentException("spring.insight.collector.url 必须配置");
            }
        }
    }
}
