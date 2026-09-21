/**
 * InsightMicrometerBridge：将 Span / 上报队列指标桥接到宿主 MeterRegistry（Boot2）。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.micrometer;

import io.github.iweidujiang.springinsight.agent.boot2.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 不替代连接池 / JVM 等系统指标；仅曝光 Insight 自身 Span 耗时与上报队列。
 */
public class InsightMicrometerBridge {

    private static final Logger log = LoggerFactory.getLogger(InsightMicrometerBridge.class);

    /** 宿主 MeterRegistry */
    private final MeterRegistry registry;

    /** 入队成功计数 */
    private final Counter accepted;

    /** 入队失败（队列满等）计数 */
    private final Counter rejected;

    /**
     * 注册 Counter / Gauge；与主线指标名对齐便于 Prometheus 查询。
     *
     * @param registry          宿主注册表
     * @param asyncSpanReporter 异步上报器（提供队列长度）
     */
    public InsightMicrometerBridge(MeterRegistry registry, AsyncSpanReporter asyncSpanReporter) {
        this.registry = registry;
        this.accepted = Counter.builder("spring.insight.spans.accepted")
                .description("Spans accepted into the Insight async reporter queue")
                .register(registry);
        this.rejected = Counter.builder("spring.insight.spans.rejected")
                .description("Spans rejected (queue full or reporter unavailable)")
                .register(registry);

        // queue size：Gauge 绑定上报器，避免热路径频繁读锁
        registry.gauge("spring.insight.reporter.queue.size", asyncSpanReporter,
                new java.util.function.ToDoubleFunction<AsyncSpanReporter>() {
                    @Override
                    public double applyAsDouble(AsyncSpanReporter value) {
                        return value.getQueueSize();
                    }
                });

        log.info("[Boot2-Micrometer] Insight 指标已注册到 MeterRegistry（span timer + queue gauge）");
    }

    /**
     * 记录单个已结束 Span 的耗时与受理结果。
     *
     * @param span               已 finish 的 Span；可为 null（仅计数）
     * @param acceptedByReporter 是否已成功入队
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
        long durationMs = span.getDurationMs() != null ? Math.max(0L, span.getDurationMs().longValue()) : 0L;
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

    /**
     * 空串回退。
     *
     * @param value    原值
     * @param fallback 回退
     * @return 非空原值或回退
     */
    private static String blankTo(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    /**
     * 限制 tag 长度，避免高基数撑爆时序库。
     *
     * @param raw 原始 tag
     * @return 最多 64 字符
     */
    private static String sanitize(String raw) {
        String v = raw.trim();
        if (v.length() > 64) {
            return v.substring(0, 64);
        }
        return v;
    }
}
