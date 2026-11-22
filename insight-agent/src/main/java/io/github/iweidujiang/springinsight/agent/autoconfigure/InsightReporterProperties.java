package io.github.iweidujiang.springinsight.agent.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异步上报队列配置（{@code spring.insight.reporter.*}）。
 *
 * @author 苏渡苇
 * @since 2026-11-22
 */
@Data
@ConfigurationProperties(prefix = "spring.insight.reporter")
public class InsightReporterProperties {

    /**
     * 上报队列容量。默认 10000；联调背压时可临时调低。
     */
    private int queueCapacity = 10_000;

    /**
     * @return 合法容量（至少 1）
     */
    public int normalizedQueueCapacity() {
        return queueCapacity < 1 ? 1 : queueCapacity;
    }
}
