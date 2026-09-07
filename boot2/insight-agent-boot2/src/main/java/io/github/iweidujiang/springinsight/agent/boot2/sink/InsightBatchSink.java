/*
 * Copyright (c) 2026, 苏渡苇. All rights reserved.
 *
 * InsightBatchSink：异步队列批量写出接口（Boot2 仅 Span，暂不含 JVM 指标）。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.sink;

import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;

import java.util.List;

public interface InsightBatchSink {

    void acceptTraceSpans(List<TraceSpan> spans);
}
