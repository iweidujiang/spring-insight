/**
 * SpanReportingListener：将已结束 Span 交予异步上报器。
 *
 * @since：2026-09-07
 * @author：苏渡苗 公众号：苏渡苗
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.listener;

import io.github.iweidujiang.springinsight.agent.boot2.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;

public class SpanReportingListener {

    private static final Logger log = LoggerFactory.getLogger(SpanReportingListener.class);

    private final AsyncSpanReporter asyncSpanReporter;

    /**
     * @param asyncSpanReporter 异步上报器
     */
    public SpanReportingListener(AsyncSpanReporter asyncSpanReporter) {
        this.asyncSpanReporter = asyncSpanReporter;
    }

    /**
     * 上报已结束的 Span。
     *
     * @param span 若为 null 则忽略
     */
    public void reportSpan(TraceSpan span) {
        if (span == null) {
            return;
        }
        if (!span.isFinished()) {
            span.finish();
        }
        boolean ok = asyncSpanReporter.report(span);
        if (!ok) {
            log.warn("[Boot2监听] 上报被拒绝: spanId={}", span.getSpanId());
        }
    }

    /**
     * 容器销毁时停止上报线程。
     */
    @PreDestroy
    public void destroy() {
        asyncSpanReporter.stop();
    }
}
