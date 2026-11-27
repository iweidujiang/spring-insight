package io.github.iweidujiang.springinsight.agent.context;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.springframework.util.AntPathMatcher;

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

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

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
        return shouldExport(span, alwaysSampleErrors, null, null);
    }

    /**
     * 是否应把 Span 交给上报器，并应用路径 / 操作名名单。
     * <p>
     * 两份名单都为空时，行为与 {@link #shouldExport(TraceSpan, boolean)} 相同。
     * 命中丢弃名单则不上报（含已采样与错误）。未命中丢弃、命中强制名单则上报（含未采样的成功）。
     * 同一 Span 两份都命中时以丢弃为准。
     * </p>
     *
     * @param span                待上报 Span
     * @param alwaysSampleErrors  未采样的错误是否仍上报
     * @param dropPatterns        匹配则不上报；空或 null 表示不启用
     * @param forcePatterns       匹配则强制上报；空或 null 表示不启用
     * @return 是否上报
     */
    public static boolean shouldExport(TraceSpan span, boolean alwaysSampleErrors,
                                       String[] dropPatterns, String[] forcePatterns) {
        if (span == null) {
            return false;
        }
        if (matchesRule(span, dropPatterns)) {
            return false;
        }
        if (matchesRule(span, forcePatterns)) {
            return true;
        }
        if (span.isSampled()) {
            return true;
        }
        return alwaysSampleErrors && isError(span);
    }

    /**
     * 操作名、{@code http.path} 或操作名里的路径段是否命中任一 Ant 模式。
     *
     * @param span     Span
     * @param patterns 模式；空则不命中
     * @return 是否命中
     */
    public static boolean matchesRule(TraceSpan span, String[] patterns) {
        if (span == null || patterns == null || patterns.length == 0) {
            return false;
        }
        String operation = span.getOperationName();
        String httpPath = span.getTags() == null ? null : span.getTags().get("http.path");
        String operationPath = pathPart(operation);
        for (String raw : patterns) {
            if (raw == null) {
                continue;
            }
            String pattern = raw.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            if (textMatches(pattern, operation)
                    || textMatches(pattern, httpPath)
                    || textMatches(pattern, operationPath)) {
                return true;
            }
        }
        return false;
    }

    private static String pathPart(String operation) {
        if (operation == null) {
            return null;
        }
        int space = operation.indexOf(' ');
        if (space < 0 || space >= operation.length() - 1) {
            return null;
        }
        return operation.substring(space + 1).trim();
    }

    private static boolean textMatches(String pattern, String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            return PATH_MATCHER.match(pattern, value);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
