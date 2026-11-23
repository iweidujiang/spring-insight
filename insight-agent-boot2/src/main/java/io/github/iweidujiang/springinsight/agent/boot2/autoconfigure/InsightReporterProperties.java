package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异步上报队列配置（{@code spring.insight.reporter.*}）。
 *
 * @author 苏渡苇
 * @since 2026-11-23
 */
@ConfigurationProperties(prefix = "spring.insight.reporter")
public class InsightReporterProperties {

    /**
     * 上报队列容量。默认 10000；联调背压时可临时调低。
     */
    private int queueCapacity = 10_000;

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    /**
     * @return 合法容量（至少 1）
     */
    public int normalizedQueueCapacity() {
        return queueCapacity < 1 ? 1 : queueCapacity;
    }
}
