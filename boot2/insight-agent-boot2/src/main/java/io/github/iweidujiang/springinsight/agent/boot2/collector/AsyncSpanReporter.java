/*
 * Copyright (c) 2026, 苏渡苇. All rights reserved.
 *
 * AsyncSpanReporter：内存队列异步批量刷到 InsightBatchSink。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.collector;

import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import io.github.iweidujiang.springinsight.agent.boot2.sink.InsightBatchSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AsyncSpanReporter {

    private static final int QUEUE_CAPACITY = 10000;
    private static final int BATCH_SIZE = 200;
    private static final long FLUSH_INTERVAL_MS = 5000L;
    private static final long OFFER_TIMEOUT_MS = 100L;

    private final BlockingQueue<TraceSpan> queue = new LinkedBlockingQueue<TraceSpan>(QUEUE_CAPACITY);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ObjectProvider<InsightBatchSink> batchSinkProvider;
    private final String serviceName;
    private Thread flushThread;

    public AsyncSpanReporter(String serviceName, ObjectProvider<InsightBatchSink> batchSinkProvider) {
        this.serviceName = serviceName;
        this.batchSinkProvider = batchSinkProvider;
    }

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
            log.info("[异步上报-Boot2] 已启动: serviceName={}", serviceName);
        }
    }

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
        log.info("[异步上报-Boot2] 已停止");
    }

    public boolean report(TraceSpan span) {
        if (span == null || !running.get()) {
            return false;
        }
        try {
            TraceSpan copy = TraceSpan.snapshot(span);
            boolean ok = queue.offer(copy, OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!ok) {
                log.warn("[异步上报-Boot2] 队列已满，丢弃 spanId={}", span.getSpanId());
            }
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void flushLoop() {
        while (running.get()) {
            try {
                List<TraceSpan> batch = new ArrayList<TraceSpan>(BATCH_SIZE);
                TraceSpan first = queue.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, BATCH_SIZE - 1);
                }
                if (!batch.isEmpty()) {
                    flushBatch(batch);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("[异步上报-Boot2] 刷盘循环异常: {}", e.getMessage());
            }
        }
    }

    private void flushRemaining() {
        List<TraceSpan> batch = new ArrayList<TraceSpan>();
        queue.drainTo(batch);
        if (!batch.isEmpty()) {
            flushBatch(batch);
        }
    }

    private void flushBatch(List<TraceSpan> batch) {
        InsightBatchSink sink = batchSinkProvider.getIfAvailable();
        if (sink == null) {
            log.warn("[异步上报-Boot2] 无 InsightBatchSink，丢弃 {} 条", batch.size());
            return;
        }
        try {
            sink.acceptTraceSpans(batch);
        } catch (Exception e) {
            log.warn("[异步上报-Boot2] Sink 写出失败: size={}, error={}", batch.size(), e.getMessage());
        }
    }
}
