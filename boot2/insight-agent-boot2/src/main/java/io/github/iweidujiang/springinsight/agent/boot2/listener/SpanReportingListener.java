/**
 * SpanReportingListener?????? Span ?????????
 *
 * @since?2026-09-07
 * @author???? ???????
 *
 * GitHub?https://github.com/iweidujiang
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
     * @param asyncSpanReporter ?????
     */
    public SpanReportingListener(AsyncSpanReporter asyncSpanReporter) {
        this.asyncSpanReporter = asyncSpanReporter;
    }

    /**
     * ??????? Span?
     *
     * @param span ?? null????
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
            log.warn("[Span??-Boot2] ?????: spanId={}", span.getSpanId());
        }
    }

    /**
     * ????????????
     */
    @PreDestroy
    public void destroy() {
        asyncSpanReporter.stop();
    }
}
