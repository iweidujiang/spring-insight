package io.github.iweidujiang.springinsight.agent.micrometer;

import io.github.iweidujiang.springinsight.agent.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 将 Insight Span / 上报队列指标桥接到 Micrometer，供宿主应用的 Prometheus / Actuator 刮取。
 * <p>
 * 不替代连接池等系统指标：HikariCP、HTTP 客户端连接数等仍应由 Spring Boot Actuator + Micrometer 提供。
 * </p>
 */
@Slf4j
public class InsightMicrometerBridge {

    private final MeterRegistry registry;
    private final Counter accepted;
    private final Counter rejected;

    public InsightMicrometerBridge(MeterRegistry registry, AsyncSpanReporter asyncSpanReporter) {
        this.registry = registry;
        this.accepted = Counter.builder("spring.insight.spans.accepted")
                .description("Spans accepted into the Insight async reporter queue")
                .register(registry);
        this.rejected = Counter.builder("spring.insight.spans.rejected")
                .description("Spans rejected (queue full or reporter unavailable)")
                .register(registry);

        registry.gauge("spring.insight.reporter.queue.size", asyncSpanReporter, AsyncSpanReporter::getQueueSize);

        AsyncSpanReporter.ReporterMetrics snapshot = asyncSpanReporter.getMetrics();
        registry.gauge("spring.insight.reporter.received", asyncSpanReporter,
                r -> r.getMetrics().getTotalReceived());
        registry.gauge("spring.insight.reporter.success", asyncSpanReporter,
                r -> r.getMetrics().getTotalSuccess());
        registry.gauge("spring.insight.reporter.failed", asyncSpanReporter,
                r -> r.getMetrics().getTotalFailed());
        registry.gauge("spring.insight.reporter.dropped", asyncSpanReporter,
                r -> r.getMetrics().getTotalDropped());

        log.info("[Micrometer] Insight 指标已注册到 MeterRegistry（span timer + reporter gauges）; bootstrap snapshot={}",
                snapshot);
    }

    /**
     * 记录单个已结束 Span 的耗时与受理结果。
     */
    public void recordSpan(TraceSpan span, boolean acceptedByReporter) {
        if (acceptedByReporter) {
            accepted.increment();
        } else {
            rejected.increment();
        }
        if (span == null) {
            return;
        }
        long durationMs = span.getDurationMs() != null ? Math.max(0L, span.getDurationMs()) : 0L;
        String kind = blankTo(span.getSpanKind(), "UNKNOWN");
        String remote = blankTo(span.getRemoteService(), "none");
        String success = Boolean.FALSE.equals(span.getSuccess()) ? "false" : "true";

        Timer.builder("spring.insight.span")
                .description("Insight instrumented span duration")
                .tag("span.kind", sanitize(kind))
                .tag("remote.service", sanitize(remote))
                .tag("success", success)
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 限制 tag 基数，避免超长 remote 撑爆时序库 */
    private static String sanitize(String raw) {
        String v = raw.trim();
        if (v.length() > 64) {
            return v.substring(0, 64);
        }
        return v;
    }
}
