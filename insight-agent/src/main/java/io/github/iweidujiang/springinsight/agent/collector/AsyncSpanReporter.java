package io.github.iweidujiang.springinsight.agent.collector;

import io.github.iweidujiang.springinsight.agent.model.JvmMetric;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 异步Span上报器,负责缓冲收集到的TraceSpan，并批量上报到Collector服务
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Slf4j
public class AsyncSpanReporter {

    // 配置常量
    private static final int DEFAULT_QUEUE_CAPACITY = 10000;
    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final long DEFAULT_FLUSH_INTERVAL_MS = 5000; // 5秒
    private static final long DEFAULT_OFFER_TIMEOUT_MS = 100;

    // 队列与状态控制
    private final BlockingQueue<Object> metricsQueue;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread flushThread;

    // 服务标识
    private final String serviceName;
    private final String serviceInstance;

    // 统计信息
    private final ReporterMetrics metrics = new ReporterMetrics();

    /**
     * 构造函数
     */
    public AsyncSpanReporter(String serviceName, String serviceInstance) {
        this.serviceName = serviceName;
        this.serviceInstance = serviceInstance;
        this.metricsQueue = new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);

        log.info("[异步上报器] 初始化完成: serviceName={}, serviceInstance={}",
                serviceName, serviceInstance);
    }

    /**
     * 启动上报器
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            flushThread = new Thread(this::flushLoop, "spring-insight-reporter");
            flushThread.setDaemon(true);
            flushThread.start();
            log.info("[异步上报器] 已启动后台上报线程");
        } else {
            log.warn("[异步上报器] 上报器已在运行状态，忽略重复启动");
        }
    }

    /**
     * 停止上报器
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            // 等待flush线程结束
            if (flushThread != null) {
                try {
                    flushThread.interrupt();
                    flushThread.join(3000); // 等待3秒
                    log.info("[异步上报器] 上报线程已停止");
                } catch (InterruptedException e) {
                    log.warn("[异步上报器] 等待上报线程停止时被中断", e);
                    Thread.currentThread().interrupt();
                }
            }
            // 尝试清空队列并上报剩余数据
            flushRemainingSpans();
            log.info("[异步上报器] 已停止，上报统计: {}", metrics);
        }
    }

    /**
     * 上报单个Span（异步非阻塞）
     */
    public boolean report(TraceSpan span) {
        if (span == null) {
            log.warn("[异步上报器] 尝试上报空的Span，已忽略");
            return false;
        }

        return report((Object) span);
    }

    /**
     * 上报JVM指标（异步非阻塞）
     */
    public boolean report(JvmMetric metric) {
        if (metric == null) {
            log.warn("[异步上报器] 尝试上报空的JVM指标，已忽略");
            return false;
        }

        return report((Object) metric);
    }

    /**
     * 上报指标（异步非阻塞）
     */
    private boolean report(Object metric) {
        if (metric == null) {
            log.warn("[异步上报器] 尝试上报空的指标，已忽略");
            return false;
        }

        if (!running.get()) {
            log.warn("[异步上报器] 上报器未运行，丢弃指标: {}", metric.getClass().getSimpleName());
            metrics.incrementDropped();
            return false;
        }

        try {
            boolean offered = metricsQueue.offer(metric, DEFAULT_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (offered) {
                metrics.incrementReceived();
                log.debug("[异步上报器] 指标已加入队列: type={}, 当前队列大小={}",
                        metric.getClass().getSimpleName(), metricsQueue.size());
                return true;
            } else {
                log.warn("[异步上报器] 队列已满，丢弃指标: type={}, 队列容量={}",
                        metric.getClass().getSimpleName(), DEFAULT_QUEUE_CAPACITY);
                metrics.incrementDropped();
                return false;
            }
        } catch (InterruptedException e) {
            log.warn("[异步上报器] 添加指标到队列时被中断", e);
            Thread.currentThread().interrupt();
            metrics.incrementDropped();
            return false;
        }
    }

    /**
     * 后台刷新循环
     */
    private void flushLoop() {
        log.info("[异步上报器] 后台上报线程开始运行");

        while (running.get()) {
            try {
                // 等待指定间隔或队列达到批量大小
                List<TraceSpan> traceBatch = new ArrayList<>(DEFAULT_BATCH_SIZE);
                List<JvmMetric> jvmBatch = new ArrayList<>(DEFAULT_BATCH_SIZE);
                
                // 从队列中获取数据，最长等待DEFAULT_FLUSH_INTERVAL_MS
                Object firstMetric = metricsQueue.poll(DEFAULT_FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);

                if (firstMetric != null) {
                    // 处理第一个指标
                    if (firstMetric instanceof TraceSpan) {
                        traceBatch.add((TraceSpan) firstMetric);
                    } else if (firstMetric instanceof JvmMetric) {
                        jvmBatch.add((JvmMetric) firstMetric);
                    }
                    
                    // 非阻塞方式获取更多指标，按类型分组
                    List<Object> remainingMetrics = new ArrayList<>(DEFAULT_BATCH_SIZE - 1);
                    metricsQueue.drainTo(remainingMetrics, DEFAULT_BATCH_SIZE - 1);
                    
                    for (Object metric : remainingMetrics) {
                        if (metric instanceof TraceSpan) {
                            traceBatch.add((TraceSpan) metric);
                        } else if (metric instanceof JvmMetric) {
                            jvmBatch.add((JvmMetric) metric);
                        }
                    }
                }

                // 如果有TraceSpan数据则上报
                if (!traceBatch.isEmpty()) {
                    flushTraceSpans(traceBatch);
                }
                
                // 如果有JvmMetric数据则上报
                if (!jvmBatch.isEmpty()) {
                    flushJvmMetrics(jvmBatch);
                }

            } catch (InterruptedException e) {
                if (running.get()) {
                    log.warn("[异步上报器] 刷新循环被意外中断", e);
                }
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[异步上报器] 刷新循环发生异常", e);
                // 继续运行，避免因单次异常导致上报停止
                try {
                    Thread.sleep(1000); // 异常后暂停1秒
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("[异步上报器] 后台上报线程结束运行");
    }

    /**
     * 批量处理TraceSpan
     */
    private void flushTraceSpans(List<TraceSpan> batch) {
        if (batch.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int batchSize = batch.size();

        try {
            // 为每个Span设置服务名称和实例
            for (TraceSpan span : batch) {
                if (span.getServiceName() == null) {
                    span.setServiceName(serviceName);
                }
                if (span.getServiceInstance() == null) {
                    span.setServiceInstance(serviceInstance);
                }
            }

            // 简化处理，只记录日志，实际持久化由starter模块的其他组件处理
            long cost = System.currentTimeMillis() - startTime;
            metrics.incrementSuccess(batchSize, cost);
            log.debug("[异步上报器] 处理了 {} 个TraceSpan，实际持久化由其他组件负责", batchSize);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            metrics.incrementFailed(batchSize);
            log.error("[异步上报器] TraceSpan批量处理异常: size={}, cost={}ms, error={}",
                    batchSize, cost, e.getMessage(), e);
        }
    }

    /**
     * 批量处理JvmMetric
     * 目前仅记录日志，待实现持久化功能
     */
    private void flushJvmMetrics(List<JvmMetric> batch) {
        if (batch.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int batchSize = batch.size();

        try {
            // 目前仅记录日志，待实现JvmMetric持久化功能
            log.debug("[异步上报器] 收到JvmMetric批量数据: size={}", batchSize);
            
            // 模拟成功处理
            long cost = System.currentTimeMillis() - startTime;
            metrics.incrementSuccess(batchSize, cost);
            log.debug("[异步上报器] JvmMetric批量处理完成: size={}, cost={}ms", batchSize, cost);
            
            // TODO: 实现JvmMetric持久化功能

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            metrics.incrementFailed(batchSize);
            log.error("[异步上报器] JvmMetric批量处理发生异常: size={}, cost={}ms", batchSize, cost, e);
        }
    }

    /**
     * 获取当前队列大小
     */
    public int getQueueSize() {
        return metricsQueue.size();
    }

    /**
     * 清空并上报剩余的所有指标（用于关闭时）
     */
    private void flushRemainingSpans() {
        if (metricsQueue.isEmpty()) {
            log.debug("[异步上报器] 队列已空，无需清理");
            return;
        }

        List<TraceSpan> remainingTraceSpans = new ArrayList<>();
        List<JvmMetric> remainingJvmMetrics = new ArrayList<>();
        
        // 将剩余指标按类型分组
        List<Object> remainingMetrics = new ArrayList<>();
        metricsQueue.drainTo(remainingMetrics);
        
        for (Object metric : remainingMetrics) {
            if (metric instanceof TraceSpan) {
                remainingTraceSpans.add((TraceSpan) metric);
            } else if (metric instanceof JvmMetric) {
                remainingJvmMetrics.add((JvmMetric) metric);
            }
        }

        if (!remainingTraceSpans.isEmpty()) {
            log.info("[异步上报器] 清理剩余TraceSpan: size={}", remainingTraceSpans.size());
            flushTraceSpans(remainingTraceSpans);
        }
        
        if (!remainingJvmMetrics.isEmpty()) {
            log.info("[异步上报器] 清理剩余JvmMetric: size={}", remainingJvmMetrics.size());
            flushJvmMetrics(remainingJvmMetrics);
        }
    }

    /**
     * 获取上报统计信息
     */
    public ReporterMetrics getMetrics() {
        return metrics.copy();
    }

    /**
     * 上报器统计指标
     */
    @Data
    public static class ReporterMetrics {
        private long totalReceived = 0;      // 总接收数
        private long totalSuccess = 0;       // 总成功上报数
        private long totalFailed = 0;        // 总失败数
        private long totalDropped = 0;       // 总丢弃数
        private long totalBatches = 0;       // 总批次数
        private long totalCostMs = 0;        // 总耗时(ms)

        public synchronized void incrementReceived() {
            totalReceived++;
        }

        public synchronized void incrementSuccess(int batchSize, long costMs) {
            totalSuccess += batchSize;
            totalBatches++;
            totalCostMs += costMs;
        }

        public synchronized void incrementFailed(int batchSize) {
            totalFailed += batchSize;
            totalBatches++;
        }

        public synchronized void incrementDropped() {
            totalDropped++;
        }

        public synchronized ReporterMetrics copy() {
            ReporterMetrics copy = new ReporterMetrics();
            copy.totalReceived = this.totalReceived;
            copy.totalSuccess = this.totalSuccess;
            copy.totalFailed = this.totalFailed;
            copy.totalDropped = this.totalDropped;
            copy.totalBatches = this.totalBatches;
            copy.totalCostMs = this.totalCostMs;
            return copy;
        }

        @Override
        public String toString() {
            return String.format("接收数=%d, 成功=%d, 失败=%d, 丢弃=%d, 批次=%d, 平均耗时=%.2fms",
                    totalReceived, totalSuccess, totalFailed, totalDropped, totalBatches,
                    totalBatches > 0 ? (double) totalCostMs / totalBatches : 0.0);
        }
    }
}
