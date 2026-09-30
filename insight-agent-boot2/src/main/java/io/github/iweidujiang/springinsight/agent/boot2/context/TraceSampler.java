package io.github.iweidujiang.springinsight.agent.boot2.context;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 头部采样决策：按 {@code spring.insight.sample-rate} 决定是否记录整条 Trace（Boot2）。
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class TraceSampler {

    private TraceSampler() {
    }

    /**
     * 按采样率做一次独立决策（用于新建根 Trace）。
     *
     * @param sampleRate 0.0～1.0
     * @return 是否采样
     */
    public static boolean decide(double sampleRate) {
        double rate = clamp(sampleRate);
        if (rate <= 0.0d) {
            return false;
        }
        if (rate >= 1.0d) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    /**
     * @param sampleRate 原始配置
     * @return [0, 1]
     */
    public static double clamp(double sampleRate) {
        if (Double.isNaN(sampleRate) || sampleRate < 0.0d) {
            return 0.0d;
        }
        if (sampleRate > 1.0d) {
            return 1.0d;
        }
        return sampleRate;
    }
}
