package io.github.iweidujiang.springinsight.agent.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 丢弃 / 队列背压统计（{@code spring.insight.drop-stats.*}）。
 * <p>
 * 开启后随心跳附带队列丢弃累计与当前深度；默认开启但控制台仅在有丢弃时提示，避免打扰。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026-11-22
 */
@Data
@ConfigurationProperties(prefix = "spring.insight.drop-stats")
public class InsightDropStatsProperties {

    /**
     * 是否在心跳中附带丢弃 / 队列深度。默认 true。
     */
    private boolean enabled = true;
}
