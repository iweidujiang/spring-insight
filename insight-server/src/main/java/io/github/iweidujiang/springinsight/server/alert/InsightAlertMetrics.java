package io.github.iweidujiang.springinsight.server.alert;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 告警推送结果计数：{@code spring.insight.alert.webhook{result=success|failure|cooldown}}。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Component
public class InsightAlertMetrics {

    private final Counter success;
    private final Counter failure;
    private final Counter cooldown;

    /**
     * @param registry 宿主 MeterRegistry（Actuator）
     */
    public InsightAlertMetrics(MeterRegistry registry) {
        this.success = counter(registry, "success");
        this.failure = counter(registry, "failure");
        this.cooldown = counter(registry, "cooldown");
    }

    /**
     * 记录一次推送结果。
     *
     * @param result {@code success}、{@code failure} 或 {@code cooldown}
     */
    public void record(String result) {
        if ("success".equals(result)) {
            success.increment();
        } else if ("failure".equals(result)) {
            failure.increment();
        } else if ("cooldown".equals(result)) {
            cooldown.increment();
        }
    }

    /**
     * @param result 结果标签
     * @return 当前累计次数
     */
    public double count(String result) {
        if ("success".equals(result)) {
            return success.count();
        }
        if ("failure".equals(result)) {
            return failure.count();
        }
        if ("cooldown".equals(result)) {
            return cooldown.count();
        }
        return 0D;
    }

    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("spring.insight.alert.webhook")
                .description("Webhook alert outcomes")
                .tag("result", result)
                .register(registry);
    }
}
