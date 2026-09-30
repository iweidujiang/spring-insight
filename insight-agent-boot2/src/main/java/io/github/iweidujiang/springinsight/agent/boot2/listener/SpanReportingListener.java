package io.github.iweidujiang.springinsight.agent.boot2.listener;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.boot2.micrometer.InsightMicrometerBridge;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import javax.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SpanReportingListener：将已结束 Span 交予异步上报器；未采样 Span 直接丢弃。
 *
 * @since 2026-09-07
 * @author 苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
public class SpanReportingListener {

    private static final Logger log = LoggerFactory.getLogger(SpanReportingListener.class);

    private final AsyncSpanReporter asyncSpanReporter;

    /** Micrometer 桥；无 MeterRegistry 时为空 */
    private final ObjectProvider<InsightMicrometerBridge> micrometerBridge;

    private final InsightBoot2Properties insightProperties;

    private final AtomicLong totalDroppedUnsampled = new AtomicLong(0);

    /**
     * @param asyncSpanReporter 异步上报器
     * @param micrometerBridge  Micrometer 桥（可缺席）
     * @param insightProperties 配置（采样率等）
     */
    public SpanReportingListener(AsyncSpanReporter asyncSpanReporter,
                                 ObjectProvider<InsightMicrometerBridge> micrometerBridge,
                                 InsightBoot2Properties insightProperties) {
        this.asyncSpanReporter = asyncSpanReporter;
        this.micrometerBridge = micrometerBridge;
        this.insightProperties = insightProperties;
    }

    /**
     * 上报已结束的 Span，并记录 Micrometer；未采样则丢弃。
     *
     * @param span 若为 null 则忽略
     */
    public void reportSpan(TraceSpan span) {
        if (span == null) {
            return;
        }
        if (!span.isSampled()) {
            totalDroppedUnsampled.incrementAndGet();
            log.debug("[Boot2监听] 未采样 Span 已丢弃: spanId={}", span.getSpanId());
            return;
        }
        if (!span.isFinished()) {
            span.finish();
        }
        boolean ok = asyncSpanReporter.report(span);
        InsightMicrometerBridge bridge = micrometerBridge.getIfAvailable();
        if (bridge != null) {
            bridge.recordSpan(span, ok);
        }
        if (!ok) {
            log.warn("[Boot2监听] 上报被拒绝: spanId={}", span.getSpanId());
        }
    }

    /**
     * @return 未采样丢弃计数（诊断用）
     */
    public long getTotalDroppedUnsampled() {
        return totalDroppedUnsampled.get();
    }

    /**
     * 容器销毁时停止上报线程。
     */
    @PreDestroy
    public void destroy() {
        log.info("[Boot2监听] 关闭；sample-rate={}, 未采样丢弃={}",
                insightProperties != null ? Double.valueOf(insightProperties.getSampleRate()) : null,
                Long.valueOf(totalDroppedUnsampled.get()));
        asyncSpanReporter.stop();
    }
}
