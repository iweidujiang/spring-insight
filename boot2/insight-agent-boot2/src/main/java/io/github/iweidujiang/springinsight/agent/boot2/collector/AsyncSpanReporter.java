/**
 * AsyncSpanReporter??????????? InsightBatchSink?
 *
 * @since?2026-09-07
 * @author???? ???????
 *
 * GitHub?https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.collector;

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

public class AsyncSpanReporter {

    private static final Logger log = LoggerFactory.getLogger(AsyncSpanReporter.class);

    private static final int QUEUE_CAPACITY = 10000;
    private static final int BATCH_SIZE = 200;
    private static final long FLUSH_INTERVAL_MS = 5000L;
    private static final long OFFER_TIMEOUT_MS = 100L;

    private final BlockingQueue<TraceSpan> queue = new LinkedBlockingQueue<TraceSpan>(QUEUE_CAPACITY);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ObjectProvider<InsightBatchSink> batchSinkProvider;
    private final String serviceName;
    private Thread flushThread;

    /**
     * @param serviceName        ????????
     * @param batchSinkProvider  Sink ????
     */
    public AsyncSpanReporter(String serviceName, ObjectProvider<InsightBatchSink> batchSinkProvider) {
        this.serviceName = serviceName;
        this.batchSinkProvider = batchSinkProvider;
    }

    /**
     * ?????????
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
            log.info("[????-Boot2] ???: serviceName={}", serviceName);
        }
    }

    /**
     * ????????????
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
        log.info("[????-Boot2] ???");
    }

    /**
     * ???? Span ???
     *
     * @param span ?? Span
     * @return ??????
     */
    public boolean report(TraceSpan span) {
        if (span == null || !running.get()) {
            return false;
        }
        try {
            // snapshot????????????????
            TraceSpan copy = TraceSpan.snapshot(span);
            boolean ok = queue.offer(copy, OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!ok) {
                log.warn("[????-Boot2] ??????? spanId={}", span.getSpanId());
            }
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * ??????????????
     */
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
                log.warn("[????-Boot2] ??????: {}", e.getMessage());
            }
        }
    }

    /**
     * ????????
     */
    private void flushRemaining() {
        List<TraceSpan> batch = new ArrayList<TraceSpan>();
        queue.drainTo(batch);
        if (!batch.isEmpty()) {
            flushBatch(batch);
        }
    }

    /**
     * ?? Sink ?????
     *
     * @param batch ?? Span
     */
    private void flushBatch(List<TraceSpan> batch) {
        InsightBatchSink sink = batchSinkProvider.getIfAvailable();
        if (sink == null) {
            log.warn("[????-Boot2] ? InsightBatchSink??? {} ?", batch.size());
            return;
        }
        try {
            sink.acceptTraceSpans(batch);
        } catch (Exception e) {
            log.warn("[????-Boot2] Sink ????: size={}, error={}", batch.size(), e.getMessage());
        }
    }
}
