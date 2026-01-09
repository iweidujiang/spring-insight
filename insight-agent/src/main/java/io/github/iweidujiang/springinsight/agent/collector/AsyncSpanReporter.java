package io.github.iweidujiang.springinsight.agent.collector;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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
    private final BlockingQueue<TraceSpan> spanQueue;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread flushThread;

    // 上报目标与客户端
    private final String collectorUrl;
    private final String serviceName;
    private final String serviceInstance;
    private final RestTemplate restTemplate;

    // 统计信息
    private final ReporterMetrics metrics = new ReporterMetrics();

    /**
     * 构造函数
     */
    public AsyncSpanReporter(String collectorUrl, String serviceName, String serviceInstance) {
        this.collectorUrl = collectorUrl;
        this.serviceName = serviceName;
        this.serviceInstance = serviceInstance;
        this.restTemplate = new RestTemplate();
        this.spanQueue = new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);

        log.info("[异步上报器] 初始化完成: collectorUrl={}, serviceName={}, serviceInstance={}",
                collectorUrl, serviceName, serviceInstance);
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

        if (!running.get()) {
            log.warn("[异步上报器] 上报器未运行，丢弃Span: spanId={}", span.getSpanId());
            metrics.incrementDropped();
            return false;
        }

        try {
            boolean offered = spanQueue.offer(span, DEFAULT_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (offered) {
                metrics.incrementReceived();
                log.debug("[异步上报器] Span已加入队列: spanId={}, 当前队列大小={}",
                        span.getSpanId(), spanQueue.size());
                return true;
            } else {
                log.warn("[异步上报器] 队列已满，丢弃Span: spanId={}, 队列容量={}",
                        span.getSpanId(), DEFAULT_QUEUE_CAPACITY);
                metrics.incrementDropped();
                return false;
            }
        } catch (InterruptedException e) {
            log.warn("[异步上报器] 添加Span到队列时被中断", e);
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
                List<TraceSpan> batch = new ArrayList<>(DEFAULT_BATCH_SIZE);
                TraceSpan firstSpan = spanQueue.poll(DEFAULT_FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);

                if (firstSpan != null) {
                    batch.add(firstSpan);
                    // 非阻塞方式获取更多Span
                    spanQueue.drainTo(batch, DEFAULT_BATCH_SIZE - 1);
                }

                // 如果有数据则上报
                if (!batch.isEmpty()) {
                    flushBatch(batch);
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
     * 批量上报Span
     */
    private void flushBatch(List<TraceSpan> batch) {
        if (batch.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int batchSize = batch.size();

        try {
            // 构建上报请求
            SpanBatchRequest request = new SpanBatchRequest();
            request.setServiceName(serviceName);
            request.setServiceInstance(serviceInstance);
            request.setSpans(batch);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Spring-Insight-Agent-Version", "0.1.0");

            HttpEntity<SpanBatchRequest> entity = new HttpEntity<>(request, headers);

            // 发送请求
            String url = collectorUrl + "/api/spans/batch";
            log.debug("[异步上报器] 开始批量上报: size={}, url={}", batchSize, url);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            long cost = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful()) {
                metrics.incrementSuccess(batchSize, cost);
                log.debug("[异步上报器] 批量上报成功: size={}, cost={}ms", batchSize, cost);
            } else {
                metrics.incrementFailed(batchSize);
                log.warn("[异步上报器] 批量上报失败: size={}, status={}, cost={}ms",
                        batchSize, response.getStatusCode(), cost);
            }

        } catch (RestClientException e) {
            long cost = System.currentTimeMillis() - startTime;
            metrics.incrementFailed(batchSize);
            log.error("[异步上报器] 批量上报异常: size={}, cost={}ms, error={}",
                    batchSize, cost, e.getMessage(), e);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            metrics.incrementFailed(batchSize);
            log.error("[异步上报器] 批量上报发生未知异常: size={}, cost={}ms", batchSize, cost, e);
        }
    }

    /**
     * 清空并上报剩余的所有Span（用于关闭时）
     */
    private void flushRemainingSpans() {
        if (spanQueue.isEmpty()) {
            log.debug("[异步上报器] 队列已空，无需清理");
            return;
        }

        List<TraceSpan> remaining = new ArrayList<>();
        spanQueue.drainTo(remaining);

        if (!remaining.isEmpty()) {
            log.info("[异步上报器] 清理剩余Span: size={}", remaining.size());
            flushBatch(remaining);
        }
    }

    /**
     * 获取当前队列大小
     */
    public int getQueueSize() {
        return spanQueue.size();
    }

    /**
     * 获取上报统计信息
     */
    public ReporterMetrics getMetrics() {
        return metrics.copy();
    }

    /**
     * 批量上报请求体
     */
    @Data
    private static class SpanBatchRequest {
        private String serviceName;
        private String serviceInstance;
        private List<TraceSpan> spans;
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
