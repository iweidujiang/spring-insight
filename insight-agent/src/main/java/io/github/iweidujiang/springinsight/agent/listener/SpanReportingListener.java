package io.github.iweidujiang.springinsight.agent.listener;

import io.github.iweidujiang.springinsight.agent.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Span 报告监听器
 * |    负责监听Span完成事件并触发上报，同时管理上报器的生命周期
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Slf4j
@RequiredArgsConstructor
public class SpanReportingListener {
    private final AsyncSpanReporter asyncSpanReporter;

    // 上报统计
    private final AtomicLong totalReportedSpans = new AtomicLong(0);
    private final AtomicLong lastReportTime = new AtomicLong(System.currentTimeMillis());

    /**
     * 初始化监听器
     */
    @PostConstruct
    public void init() {
        log.info("[Span监听器] 初始化完成，已连接上报器");

        // 可以在这里注册各种Span完成事件的钩子
        // 例如：注册到全局的Span完成回调机制中
        // 目前我们通过 TraceContext 和 HttpRequestInterceptor 直接调用上报
    }

    /**
     * 报告一个已完成的 Span
     * 这是供外部调用的主要接口
     */
    public void reportSpan(TraceSpan span) {
        log.info("[调试] Span准备上报: {}", span);
        if (span == null) {
            log.warn("[Span监听器] 尝试上报空的Span，已忽略");
            return;
        }
        log.info("[调试] Span准备上报: spanId={}, operation={}", span.getSpanId(), span.getOperationName());

        if (!span.isFinished()) {
            log.warn("[Span监听器] 尝试上报未完成的Span: spanId={}，将强制结束", span.getSpanId());
            span.finish();
        }

        // 异步上报Span
        boolean success = asyncSpanReporter.report(span);

        if (success) {
            long count = totalReportedSpans.incrementAndGet();
            long now = System.currentTimeMillis();
            long lastTime = lastReportTime.getAndSet(now);

            // 每分钟打印一次统计信息
            if (now - lastTime > 60000) {
                log.info("[Span监听器] 上报统计: 总上报数={}, 队列大小={}",
                        count, asyncSpanReporter.getQueueSize());
            }

            log.debug("[Span监听器] Span已接受上报: spanId={}, operation={}, duration={}ms",
                    span.getSpanId(), span.getOperationName(), span.getDurationMs());
        } else {
            log.warn("[Span监听器] Span上报失败（可能队列已满）: spanId={}, operation={}",
                    span.getSpanId(), span.getOperationName());
        }
    }

    /**
     * 批量报告 Span
     */
    public void reportSpans(Iterable<TraceSpan> spans) {
        if (spans == null) {
            return;
        }

        int count = 0;
        for (TraceSpan span : spans) {
            reportSpan(span);
            count++;
        }

        if (count > 0) {
            log.debug("[Span监听器] 批量上报完成: 数量={}", count);
        }
    }

    /**
     * 获取上报统计信息
     */
    public ReportingStats getStats() {
        ReportingStats stats = new ReportingStats();
        stats.setTotalReportedSpans(totalReportedSpans.get());
        stats.setQueueSize(asyncSpanReporter.getQueueSize());
        stats.setReporterMetrics(asyncSpanReporter.getMetrics());
        return stats;
    }

    /**
     * 销毁监听器
     */
    @PreDestroy
    public void destroy() {
        log.info("[Span监听器] 正在关闭...");

        // 获取最终统计
        ReportingStats finalStats = getStats();
        log.info("[Span监听器] 最终上报统计: {}", finalStats);

        log.info("[Span监听器] 已关闭");
    }

    /**
     * 上报统计信息
     */
    @Data
    public static class ReportingStats {
        private long totalReportedSpans;
        private int queueSize;
        private AsyncSpanReporter.ReporterMetrics reporterMetrics;

        @Override
        public String toString() {
            return String.format("总上报Span数=%d, 当前队列大小=%d, 上报器状态=[%s]",
                    totalReportedSpans, queueSize, reporterMetrics);
        }
    }
}
