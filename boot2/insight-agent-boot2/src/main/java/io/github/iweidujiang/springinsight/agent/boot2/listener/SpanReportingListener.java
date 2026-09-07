/*
 * Copyright (c) 2026, 苏渡苇. All rights reserved.
 *
 * SpanReportingListener：接收已结束 Span 并交给异步上报器。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.listener;

import io.github.iweidujiang.springinsight.agent.boot2.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PreDestroy;

@Slf4j
public class SpanReportingListener {

    private final AsyncSpanReporter asyncSpanReporter;

    public SpanReportingListener(AsyncSpanReporter asyncSpanReporter) {
        this.asyncSpanReporter = asyncSpanReporter;
    }

    public void reportSpan(TraceSpan span) {
        if (span == null) {
            return;
        }
        if (!span.isFinished()) {
            span.finish();
        }
        boolean ok = asyncSpanReporter.report(span);
        if (!ok) {
            log.warn("[Span监听-Boot2] 上报未接受: spanId={}", span.getSpanId());
        }
    }

    @PreDestroy
    public void destroy() {
        asyncSpanReporter.stop();
    }
}
