package io.github.iweidujiang.springinsight.agent.sink;

import io.github.iweidujiang.springinsight.agent.model.JvmMetric;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;

import java.util.List;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 进程内批量数据出口（由 spring-insight-starter 提供实现）
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/3/20
 * └───────────────────────────────────────────────
 */
public interface InsightBatchSink {

    /**
     * 接收一批已结束的 TraceSpan（同 JVM 内持久化或聚合）
     */
    void acceptTraceSpans(List<TraceSpan> spans);

    /**
     * 接收一批 JVM 指标（默认可为日志或内存聚合占位）
     */
    void acceptJvmMetrics(List<JvmMetric> metrics);
}
