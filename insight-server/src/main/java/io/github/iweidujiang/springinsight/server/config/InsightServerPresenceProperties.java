package io.github.iweidujiang.springinsight.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务存活判定配置（按最近 Span 时间，无 Agent 心跳）。
 *
 * @author 苏渡苇
 * @since 2026-11-10
 */
@Data
@ConfigurationProperties(prefix = "spring.insight.server.presence")
public class InsightServerPresenceProperties {

    /**
     * 超过该分钟数无新 Span 则视为沉默（stale）；默认 5。
     */
    private int staleThresholdMinutes = 5;

    /**
     * 规范化后的阈值分钟数（至少 1）。
     *
     * @return 分钟
     */
    public int normalizedStaleThresholdMinutes() {
        return Math.max(1, staleThresholdMinutes);
    }

    /**
     * 沉默阈值对应的毫秒数。
     *
     * @return 毫秒
     */
    public long staleThresholdMillis() {
        return normalizedStaleThresholdMinutes() * 60_000L;
    }
}
