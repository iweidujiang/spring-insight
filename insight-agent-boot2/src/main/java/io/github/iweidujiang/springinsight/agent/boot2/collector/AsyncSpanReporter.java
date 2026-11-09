package io.github.iweidujiang.springinsight.agent.boot2.collector;

import io.github.iweidujiang.springinsight.agent.boot2.model.JvmMetric;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import io.github.iweidujiang.springinsight.agent.boot2.sink.InsightBatchSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AsyncSpanReporter：异步队列批量将 Span / JVM 指标写入 InsightBatchSink。
 *
 * @since 2026-09-07
 * @author  公众号：苏渡苇
 *
 * GitHub https://github.com/iweidujiang
 */
public class AsyncSpanReporter {

    private static final Logger log = LoggerFactory.getLogger(AsyncSpanReporter.class);

    private static final int QUEUE_CAPACITY = 10000;
    private static final int BATCH_SIZE = 200;
    private static final long FLUSH_INTERVAL_MS = 5000L;
    private static final long OFFER_TIMEOUT_MS = 100L;

    private final BlockingQueue<TraceSpan> spanQueue = new LinkedBlockingQueue<TraceSpan>(QUEUE_CAPACITY);
    private final BlockingQueue<JvmMetric> jvmQueue = new LinkedBlockingQueue<JvmMetric>(QUEUE_CAPACITY);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ObjectProvider<InsightBatchSink> batchSinkProvider;
    private final String serviceName;
    private Thread flushThread;

    /**
     * @param serviceName       当前服务名
     * @param batchSinkProvider Sink Provider
     */
    public AsyncSpanReporter(String serviceName, ObjectProvider<InsightBatchSink> batchSinkProvider) {
        this.serviceName = serviceName;
        this.batchSinkProvider = batchSinkProvider;
    }

    /**
     * 启动后台刷写线程。
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            flushThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    flushLoop();
                }
            }, "spring-insight-boot2-reporter");
            flushThread.setDaemon(true);
            flushThread.start();
            log.info("[Boot2上报] 已启动: serviceName={}", serviceName);
        }
    }

    /**
     * 停止线程并刷完队列中剩余数据。
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (flushThread != null) {
            flushThread.interrupt();
            try {
                flushThread.join(3000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        flushRemaining();
        log.info("[Boot2上报] 已停止");
    }

    public int getQueueSize() {
        return spanQueue.size() + jvmQueue.size();
    }

    /**
     * 将已结束 Span 入队。
     *
     * @param span 待上报 Span
     * @return 是否入队成功
     */
    public boolean report(TraceSpan span) {
        if (span == null || !running.get()) {
            return false;
        }
        try {
            TraceSpan copy = TraceSpan.snapshot(span);
            boolean ok = spanQueue.offer(copy, OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!ok) {
                log.warn("[Boot2上报] Span 队列已满，丢弃 spanId={}", span.getSpanId());
            }
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 将 JVM 指标入队。
     *
     * @param metric 指标点
     * @return 是否入队成功
     */
    public boolean report(JvmMetric metric) {
        if (metric == null || !running.get()) {
            return false;
        }
        try {
            boolean ok = jvmQueue.offer(metric, OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!ok) {
                log.warn("[Boot2上报] JVM 队列已满，丢弃指标");
            }
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 定时/达批从队列取出并刷写。
     */
    private void flushLoop() {
        while (running.get()) {
            try {
                List<TraceSpan> spanBatch = new ArrayList<TraceSpan>(BATCH_SIZE);
                TraceSpan first = spanQueue.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (first != null) {
                    spanBatch.add(first);
                    spanQueue.drainTo(spanBatch, BATCH_SIZE - 1);
                }
                if (!spanBatch.isEmpty()) {
                    flushSpanBatch(spanBatch);
                }

                List<JvmMetric> jvmBatch = new ArrayList<JvmMetric>(BATCH_SIZE);
                jvmQueue.drainTo(jvmBatch, BATCH_SIZE);
                if (!jvmBatch.isEmpty()) {
                    flushJvmBatch(jvmBatch);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("[Boot2上报] 刷写循环异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 刷完队列中剩余数据。
     */
    private void flushRemaining() {
        List<TraceSpan> spanBatch = new ArrayList<TraceSpan>();
        spanQueue.drainTo(spanBatch);
        if (!spanBatch.isEmpty()) {
            flushSpanBatch(spanBatch);
        }
        List<JvmMetric> jvmBatch = new ArrayList<JvmMetric>();
        jvmQueue.drainTo(jvmBatch);
        if (!jvmBatch.isEmpty()) {
            flushJvmBatch(jvmBatch);
        }
    }

    /**
     * 通过 Sink 写入一批 Span。
     *
     * @param batch 本批 Span
     */
    private void flushSpanBatch(List<TraceSpan> batch) {
        InsightBatchSink sink = batchSinkProvider.getIfAvailable();
        if (sink == null) {
            log.warn("[Boot2上报] 未找到 InsightBatchSink，丢弃 {} 条 Span", Integer.valueOf(batch.size()));
            return;
        }
        try {
            sink.acceptTraceSpans(batch);
        } catch (Exception e) {
            log.warn("[Boot2上报] Span Sink 写入失败: size={}, error={}", Integer.valueOf(batch.size()), e.getMessage());
        }
    }

    /**
     * 通过 Sink 写入一批 JVM 指标。
     *
     * @param batch 本批指标
     */
    private void flushJvmBatch(List<JvmMetric> batch) {
        InsightBatchSink sink = batchSinkProvider.getIfAvailable();
        if (sink == null) {
            log.warn("[Boot2上报] 未找到 InsightBatchSink，丢弃 {} 条 JVM 指标", Integer.valueOf(batch.size()));
            return;
        }
        try {
            sink.acceptJvmMetrics(batch);
        } catch (Exception e) {
            log.warn("[Boot2上报] JVM Sink 写入失败: size={}, error={}", Integer.valueOf(batch.size()), e.getMessage());
        }
    }
}
