package io.github.iweidujiang.springinsight.collector.service;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.collector.model.CollectorRequest;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Span 收集服务
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Slf4j
@Service
public class TraceSpanCollectorService {

    private final TraceSpanPersistenceService traceSpanPersistenceService;

    // 统计信息
    private final AtomicLong totalReceivedRequests = new AtomicLong(0);
    private final AtomicLong totalReceivedSpans = new AtomicLong(0);
    private final AtomicLong totalSuccessSpans = new AtomicLong(0);
    private final AtomicLong totalFailedSpans = new AtomicLong(0);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());

    public TraceSpanCollectorService(TraceSpanPersistenceService traceSpanPersistenceService) {
        this.traceSpanPersistenceService = traceSpanPersistenceService;
    }

    /**
     * 处理批量上报请求
     */
    public CollectorResponse processBatchRequest(CollectorRequest request) {
        log.info("[收集服务] 开始处理批量请求: {}", request.getSummary());

        StopWatch stopWatch = new StopWatch();
        stopWatch.start("验证请求");

        // 验证请求
        if (!request.isValid()) {
            log.warn("[收集服务] 请求验证失败: {}", request.getSummary());
            return CollectorResponse.error("请求数据无效");
        }

        stopWatch.stop();
        stopWatch.start("处理数据");

        // 更新统计
        totalReceivedRequests.incrementAndGet();
        int spanCount = request.getSpans().size();
        totalReceivedSpans.addAndGet(spanCount);

        try {
            // 数据清洗和补充
            List<TraceSpan> cleanedSpans = cleanAndEnrichSpans(request);

            stopWatch.stop();
            stopWatch.start("保存到存储");

            // 批量保存到数据库
            traceSpanPersistenceService.saveTraceSpans(cleanedSpans);

            stopWatch.stop();

            // 更新成功统计
            totalSuccessSpans.addAndGet(spanCount);

            // 记录处理结果
            log.info("[收集服务] 批量请求处理成功: {}, 处理耗时={}ms, 验证={}ms, 清洗={}ms, 存储={}ms",
                    request.getSummary(),
                    stopWatch.getTotalTimeMillis(),
                    stopWatch.getTaskInfo()[0].getTimeMillis(),
                    stopWatch.getTaskInfo()[1].getTimeMillis(),
                    stopWatch.getTaskInfo()[2].getTimeMillis());

            return CollectorResponse.success(spanCount);

        } catch (Exception e) {
            stopWatch.stop();
            totalFailedSpans.addAndGet(spanCount);

            log.error("[收集服务] 批量请求处理失败: {}, 错误: {}, 总耗时={}ms",
                    request.getSummary(), e.getMessage(), stopWatch.getTotalTimeMillis(), e);

            return CollectorResponse.error("处理失败: " + e.getMessage());
        }
    }

    /**
     * 处理单个Span（兼容接口）
     */
    public CollectorResponse processSingleSpan(TraceSpan span, String serviceName, String serviceInstance) {
        log.debug("[收集服务] 处理单个Span: spanId={}, service={}", span.getSpanId(), serviceName);

        if (span.getTraceId() == null || span.getSpanId() == null) {
            return CollectorResponse.error("Span缺少必要字段");
        }

        try {
            // 补充服务信息
            if (span.getServiceName() == null && serviceName != null) {
                span.setServiceName(serviceName);
            }
            if (span.getServiceInstance() == null && serviceInstance != null) {
                span.setServiceInstance(serviceInstance);
            }

            // 清理和补充数据
            cleanAndEnrichSingleSpan(span);

            // 保存单个Span
            traceSpanPersistenceService.saveTraceSpan(span);

            // 更新统计
            totalReceivedSpans.incrementAndGet();
            totalSuccessSpans.incrementAndGet();

            log.debug("[收集服务] 单个Span处理成功: spanId={}", span.getSpanId());

            return CollectorResponse.success(1);

        } catch (Exception e) {
            totalFailedSpans.incrementAndGet();
            log.error("[收集服务] 单个Span处理失败: spanId={}, 错误: {}", span.getSpanId(), e.getMessage(), e);
            return CollectorResponse.error("处理失败: " + e.getMessage());
        }
    }

    /**
     * 数据清洗和补充
     */
    private List<TraceSpan> cleanAndEnrichSpans(CollectorRequest request) {
        List<TraceSpan> result = new ArrayList<>();
        String serviceName = request.getServiceName();
        String serviceInstance = request.getServiceInstance();

        for (TraceSpan span : request.getSpans()) {
            try {
                // 补充服务信息（如果Span中没有）
                if (span.getServiceName() == null || span.getServiceName().trim().isEmpty()) {
                    span.setServiceName(serviceName);
                }
                if (span.getServiceInstance() == null || span.getServiceInstance().trim().isEmpty()) {
                    span.setServiceInstance(serviceInstance);
                }

                // 清理和补充单个Span
                cleanAndEnrichSingleSpan(span);

                result.add(span);

            } catch (Exception e) {
                log.warn("[收集服务] 清理Span时发生异常，跳过此Span: spanId={}, 错误: {}",
                        span.getSpanId(), e.getMessage());
                // 跳过有问题的Span，继续处理其他Span
            }
        }

        if (result.size() != request.getSpans().size()) {
            log.warn("[收集服务] 数据清洗后，有效Span数量变化: 原始={}, 清洗后={}",
                    request.getSpans().size(), result.size());
        }

        return result;
    }

    /**
     * 清理和补充单个Span
     */
    private void cleanAndEnrichSingleSpan(TraceSpan span) {
        // 确保必要字段不为空
        if (span.getSpanKind() == null || span.getSpanKind().trim().isEmpty()) {
            span.setSpanKind("INTERNAL");
        }

        if (span.getStatusCode() == null || span.getStatusCode().trim().isEmpty()) {
            span.setStatusCode(span.getSuccess() != null && span.getSuccess() ? "OK" : "ERROR");
        }

        // 计算持续时间（如果未提供）
        if (span.getDurationMs() == null && span.getStartTime() != null && span.getEndTime() != null) {
            span.setDurationMs(span.getEndTime() - span.getStartTime());
        }

        // 确保标签不为null
        if (span.getTags() == null) {
            span.setTags(new HashMap<>());
        }

        // 添加处理时间标签
        span.getTags().put("collector.process.time", Instant.now().toString());
        span.getTags().put("collector.version", "1.0");
    }

    /**
     * 获取服务统计信息
     */
    public CollectorStats getStats() {
        long now = System.currentTimeMillis();
        long runningHours = ChronoUnit.HOURS.between(
                Instant.ofEpochMilli(lastResetTime.get()),
                Instant.ofEpochMilli(now)
        );

        CollectorStats stats = new CollectorStats();
        stats.setTotalReceivedRequests(totalReceivedRequests.get());
        stats.setTotalReceivedSpans(totalReceivedSpans.get());
        stats.setTotalSuccessSpans(totalSuccessSpans.get());
        stats.setTotalFailedSpans(totalFailedSpans.get());
        stats.setRunningHours(runningHours);
        stats.setCurrentTime(Instant.now());

        // 计算成功率
        if (totalReceivedSpans.get() > 0) {
            stats.setSuccessRate((double) totalSuccessSpans.get() / totalReceivedSpans.get() * 100);
        } else {
            stats.setSuccessRate(100.0);
        }

        return stats;
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalReceivedRequests.set(0);
        totalReceivedSpans.set(0);
        totalSuccessSpans.set(0);
        totalFailedSpans.set(0);
        lastResetTime.set(System.currentTimeMillis());
        log.info("[收集服务] 统计信息已重置");
    }

    /**
     * 收集器响应
     */
    @Data
    public static class CollectorResponse {
        private boolean success;
        private String message;
        private Integer processedSpans;
        private Instant timestamp;

        public static CollectorResponse success(int processedSpans) {
            CollectorResponse response = new CollectorResponse();
            response.setSuccess(true);
            response.setMessage("处理成功");
            response.setProcessedSpans(processedSpans);
            response.setTimestamp(Instant.now());
            return response;
        }

        public static CollectorResponse error(String message) {
            CollectorResponse response = new CollectorResponse();
            response.setSuccess(false);
            response.setMessage(message);
            response.setTimestamp(Instant.now());
            return response;
        }
    }

    /**
     * 收集器统计信息
     */
    @Data
    public static class CollectorStats {
        private long totalReceivedRequests;
        private long totalReceivedSpans;
        private long totalSuccessSpans;
        private long totalFailedSpans;
        private double successRate;
        private long runningHours;
        private Instant currentTime;

        @Override
        public String toString() {
            return String.format(
                    "接收请求数=%d, 接收Span数=%d, 成功Span数=%d, 失败Span数=%d, 成功率=%.2f%%, 运行时长=%d小时",
                    totalReceivedRequests, totalReceivedSpans, totalSuccessSpans,
                    totalFailedSpans, successRate, runningHours
            );
        }
    }
}
