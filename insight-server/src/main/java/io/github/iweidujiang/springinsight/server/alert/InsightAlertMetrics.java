package io.github.iweidujiang.springinsight.server.alert;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 告警推送结果计数：
 * {@code spring.insight.alert.notify{channel=webhook|email|all, result=success|failure|cooldown}}。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Component
public class InsightAlertMetrics {

    private final MeterRegistry registry;

    /**
     * @param registry 宿主 MeterRegistry（Actuator）
     */
    public InsightAlertMetrics(MeterRegistry registry) {
        this.registry = registry;
        // 预注册常用序列，便于 Prometheus 一开始就能 scrape 到 0
        counter("webhook", "success");
        counter("webhook", "failure");
        counter("email", "success");
        counter("email", "failure");
        counter("all", "cooldown");
        counter("all", "success");
        counter("all", "failure");
    }

    /**
     * 记录一次通道结果。
     *
     * @param channel {@code webhook} / {@code email} / {@code all}
     * @param result  {@code success} / {@code failure} / {@code cooldown}
     */
    public void record(String channel, String result) {
        counter(channel, result).increment();
    }

    /**
     * 兼容旧单测：按整轮结果记到 {@code channel=all}。
     *
     * @param result success / failure / cooldown
     */
    public void record(String result) {
        record("all", result);
    }

    /**
     * @param channel 通道
     * @param result  结果
     * @return 当前累计
     */
    public double count(String channel, String result) {
        return counter(channel, result).count();
    }

    /**
     * @param result 整轮结果
     * @return {@code channel=all} 累计
     */
    public double count(String result) {
        return count("all", result);
    }

    private Counter counter(String channel, String result) {
        return Counter.builder("spring.insight.alert.notify")
                .description("Alert notification outcomes")
                .tag("channel", channel)
                .tag("result", result)
                .register(registry);
    }
}
