/*
 * Copyright (c) 2026, 苏渡苇. All rights reserved.
 *
 * InsightBatchSink：异步队列批量写出接口（Span + JVM 指标）。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.sink;

import io.github.iweidujiang.springinsight.agent.boot2.model.JvmMetric;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;

import java.util.List;

/**
 * Agent 异步队列的批量数据出口。
 */
public interface InsightBatchSink {

    /**
     * 接收一批已结束的 TraceSpan。
     *
     * @param spans 本批 Span
     */
    void acceptTraceSpans(List<TraceSpan> spans);

    /**
     * 接收一批 JVM 指标并上报监测中心。
     *
     * @param metrics 本批 JVM 指标
     */
    void acceptJvmMetrics(List<JvmMetric> metrics);
}
