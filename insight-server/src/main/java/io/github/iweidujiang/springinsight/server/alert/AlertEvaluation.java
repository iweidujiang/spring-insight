package io.github.iweidujiang.springinsight.server.alert;

import java.util.List;

/**
 * 一组条件的判定结果。全部成立时 {@code fired} 为 true，{@code matched} 列出各自成立的条件。
 *
 * @param fired   是否所有条件都成立
 * @param matched 已经成立的条件（部分成立时也会列出，但此时不触发）
 * @author 苏渡苇
 * @since 2026-10-19
 */
public record AlertEvaluation(boolean fired, List<MatchedCondition> matched) {

    /**
     * 一条已经成立的条件及其当时的指标值。
     *
     * @param condition 条件
     * @param value     参与比较的当前值
     */
    public record MatchedCondition(AlertCondition condition, double value) {
    }
}
