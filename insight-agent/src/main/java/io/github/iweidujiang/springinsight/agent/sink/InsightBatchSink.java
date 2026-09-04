package io.github.iweidujiang.springinsight.agent.sink;

import io.github.iweidujiang.springinsight.agent.model.JvmMetric;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;

import java.util.List;

/**
 * Agent 异步队列的批量数据出口。
 * <p>
 * 实现可选：{@link HttpInsightBatchSink}（报到独立 Server）、
 * 或 Starter 内进程内实现（embedded）。由配置 {@code spring.insight.server-url} 决定启用哪一种。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/3/20
 */
public interface InsightBatchSink {

    /**
     * 接收一批已结束的 TraceSpan 并写出（HTTP 或进程内存储）。
     *
     * @param spans 本批 Span，调用方保证非修改热路径上的可变共享态（通常已 snapshot）
     */
    void acceptTraceSpans(List<TraceSpan> spans);

    /**
     * 接收一批 JVM 指标；当前 Server 侧可无持久化实现。
     *
     * @param metrics 本批 JVM 指标
     */
    void acceptJvmMetrics(List<JvmMetric> metrics);
}
