package io.github.iweidujiang.springinsight.agent.context;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 头部采样决策：按 {@code spring.insight.sample-rate} 决定是否记录整条 Trace。
 * <p>
 * 根 Span 本地决策；有入站 {@code traceparent} 时跟随上游 flags；子 Span 继承父 Span。
 * 成功 Span 按该决策上报；错误 Span 在 {@code always-sample-errors} 开启时仍上报。
 * </p>
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
     * @param sampleRate 0.0～1.0；&lt;=0 永不采，&gt;=1 全采，中间值按概率
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
     * 将配置采样率夹到合法区间。
     *
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

    /**
     * Span 是否按错误结束（失败、状态 ERROR，或已写入错误码 / 错误信息）。
     *
     * @param span 已结束或已标错的 Span；null 视为非错误
     * @return 是否错误
     */
    public static boolean isError(TraceSpan span) {
        if (span == null) {
            return false;
        }
        if (Boolean.FALSE.equals(span.getSuccess())) {
            return true;
        }
        if (span.getErrorCode() != null || span.getErrorMessage() != null) {
            return true;
        }
        return "ERROR".equalsIgnoreCase(span.getStatusCode());
    }

    /**
     * 是否应把 Span 交给上报器。
     * <p>
     * 已采样则上报。未采样时，仅当开启错误强制上报且 Span 为错误才上报；成功 Span 仍按头部采样丢弃。
     * 不改写 {@link TraceSpan#isSampled()}，出站 {@code traceparent} 仍反映头部决策。
     * </p>
     *
     * @param span                待上报 Span
     * @param alwaysSampleErrors  未采样的错误是否仍上报
     * @return 是否上报
     */
    public static boolean shouldExport(TraceSpan span, boolean alwaysSampleErrors) {
        if (span == null) {
            return false;
        }
        if (span.isSampled()) {
            return true;
        }
        return alwaysSampleErrors && isError(span);
    }
}
