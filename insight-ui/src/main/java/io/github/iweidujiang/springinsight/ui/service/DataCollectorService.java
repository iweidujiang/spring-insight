package io.github.iweidujiang.springinsight.ui.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 数据采集服务
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/14
 * └───────────────────────────────────────────────
 */
@Slf4j
@Service
public class DataCollectorService {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Getter
    @Value("${spring-insight.collector.url:http://localhost:8080}")
    private String collectorUrl;

    // 本地缓存，避免频繁请求collector
    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30000; // 30秒缓存

    public DataCollectorService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 配置RestTemplate
        restTemplate.setErrorHandler(new RestTemplateErrorHandler());
    }

    /**
     * 获取collector状态
     */
    public CollectorStats getCollectorStats() {
        String cacheKey = "collector-stats";
        CollectorStats cached = getFromCache(cacheKey, CollectorStats.class);
        if (cached != null) {
            return cached;
        }

        try {
            String url = collectorUrl + "/api/v1/stats";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                CollectorStats stats = objectMapper.readValue(response.getBody(), CollectorStats.class);
                putToCache(cacheKey, stats);
                log.debug("获取collector统计成功: {}", stats);
                return stats;
            }
        } catch (Exception e) {
            log.error("获取collector统计失败", e);
        }

        // 返回默认值
        CollectorStats defaultStats = new CollectorStats();
        defaultStats.setTotalReceivedRequests(0);
        defaultStats.setTotalReceivedSpans(0);
        defaultStats.setTotalSuccessSpans(0);
        defaultStats.setTotalFailedSpans(0);
        defaultStats.setSuccessRate(100.0);
        defaultStats.setRunningHours(0);
        defaultStats.setCurrentTime(Instant.now());

        return defaultStats;
    }

    /**
     * 获取服务列表
     */
    public List<String> getServiceNames() {
        String cacheKey = "service-names";
        List<String> cached = getFromCache(cacheKey, List.class);
        if (cached != null) {
            return cached;
        }

        // 从collector获取最近的服务
        try {
            // 先获取最近1小时的链路，从中提取服务名
            List<TraceSpan> recentSpans = getRecentSpans(1, 100);
            Set<String> serviceSet = new TreeSet<>();

            for (TraceSpan span : recentSpans) {
                if (span.getServiceName() != null && !span.getServiceName().isEmpty()) {
                    serviceSet.add(span.getServiceName());
                }
            }

            List<String> services = new ArrayList<>(serviceSet);
            putToCache(cacheKey, services);
            return services;

        } catch (Exception e) {
            log.error("获取服务列表失败", e);
            return List.of("demo-service");
        }
    }

    /**
     * 获取最近链路
     */
    public List<TraceSpan> getRecentSpans(int hours, int limit) {
        String cacheKey = "recent-spans-" + hours + "-" + limit;

        // 对于最近数据，使用更短的缓存时间
        long ttl = hours <= 1 ? 10000 : CACHE_TTL_MS; // 1小时内数据缓存10秒

        List<TraceSpan> cached = getFromCache(cacheKey, List.class);
        if (cached != null) {
            return cached;
        }

        try {
            // 注意：collector目前没有直接获取最近链路的接口
            // 这里我们先从模拟数据开始，后续可以添加这个接口
            return generateMockSpans(limit);

        } catch (Exception e) {
            log.error("获取最近链路失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取指定服务的最近链路
     */
    public List<TraceSpan> getRecentSpansByService(String serviceName, int limit) {
        String cacheKey = "recent-spans-" + serviceName + "-" + limit;

        List<TraceSpan> cached = getFromCache(cacheKey, List.class);
        if (cached != null) {
            return cached;
        }

        try {
            List<TraceSpan> allSpans = getRecentSpans(24, 500);
            List<TraceSpan> filtered = new ArrayList<>();

            for (TraceSpan span : allSpans) {
                if (serviceName.equals(span.getServiceName()) && filtered.size() < limit) {
                    filtered.add(span);
                }
            }

            putToCache(cacheKey, filtered);
            return filtered;

        } catch (Exception e) {
            log.error("获取服务{}的最近链路失败", serviceName, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取服务依赖关系
     */
    public List<ServiceDependency> getServiceDependencies(int hours) {
        String cacheKey = "service-dependencies-" + hours;

        List<ServiceDependency> cached = getFromCache(cacheKey, List.class);
        if (cached != null) {
            return cached;
        }

        try {
            // 从最近链路中分析依赖关系
            List<TraceSpan> recentSpans = getRecentSpans(hours, 1000);
            Map<String, ServiceDependency> dependencyMap = new HashMap<>();

            for (TraceSpan span : recentSpans) {
                if (span.getRemoteService() != null && !span.getRemoteService().isEmpty()) {
                    String key = span.getServiceName() + "->" + span.getRemoteService();

                    ServiceDependency dep = dependencyMap.get(key);
                    if (dep == null) {
                        dep = new ServiceDependency();
                        dep.setSourceService(span.getServiceName());
                        dep.setTargetService(span.getRemoteService());
                        dep.setCallCount(0);
                        dep.setTotalDuration(0);
                        dep.setErrorCount(0);
                        dependencyMap.put(key, dep);
                    }

                    dep.setCallCount(dep.getCallCount() + 1);
                    if (span.getDurationMs() != null) {
                        dep.setTotalDuration(dep.getTotalDuration() + span.getDurationMs());
                    }
                    if ("ERROR".equals(span.getStatusCode())) {
                        dep.setErrorCount(dep.getErrorCount() + 1);
                    }
                }
            }

            // 计算平均耗时和错误率
            List<ServiceDependency> dependencies = new ArrayList<>();
            for (ServiceDependency dep : dependencyMap.values()) {
                if (dep.getCallCount() > 0) {
                    dep.setAvgDuration(dep.getTotalDuration() / dep.getCallCount());
                    dep.setErrorRate((double) dep.getErrorCount() / dep.getCallCount() * 100);
                }
                dependencies.add(dep);
            }

            putToCache(cacheKey, dependencies);
            return dependencies;

        } catch (Exception e) {
            log.error("获取服务依赖关系失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取服务统计
     */
    public List<ServiceStats> getServiceStats() {
        String cacheKey = "service-stats";

        List<ServiceStats> cached = getFromCache(cacheKey, List.class);
        if (cached != null) {
            return cached;
        }

        try {
            List<TraceSpan> recentSpans = getRecentSpans(24, 2000);
            Map<String, ServiceStats> statsMap = new HashMap<>();

            for (TraceSpan span : recentSpans) {
                String serviceName = span.getServiceName();
                if (serviceName == null) continue;

                ServiceStats stats = statsMap.get(serviceName);
                if (stats == null) {
                    stats = new ServiceStats();
                    stats.setServiceName(serviceName);
                    stats.setTotalSpans(0);
                    stats.setErrorSpans(0);
                    stats.setTotalDuration(0);
                    statsMap.put(serviceName, stats);
                }

                stats.setTotalSpans(stats.getTotalSpans() + 1);
                if ("ERROR".equals(span.getStatusCode())) {
                    stats.setErrorSpans(stats.getErrorSpans() + 1);
                }
                if (span.getDurationMs() != null) {
                    stats.setTotalDuration(stats.getTotalDuration() + span.getDurationMs());
                }
            }

            // 计算平均值和错误率
            List<ServiceStats> statsList = new ArrayList<>();
            for (ServiceStats stats : statsMap.values()) {
                if (stats.getTotalSpans() > 0) {
                    stats.setAvgDuration(stats.getTotalDuration() / stats.getTotalSpans());
                    stats.setErrorRate((double) stats.getErrorSpans() / stats.getTotalSpans() * 100);
                }
                statsList.add(stats);
            }

            // 按总Span数排序
            statsList.sort((a, b) -> Integer.compare(b.getTotalSpans(), a.getTotalSpans()));

            putToCache(cacheKey, statsList);
            return statsList;

        } catch (Exception e) {
            log.error("获取服务统计失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取错误分析数据
     */
    public List<ErrorAnalysis> getErrorAnalysis(int hours) {
        String cacheKey = "error-analysis-" + hours;

        List<ErrorAnalysis> cached = getFromCache(cacheKey, List.class);
        if (cached != null) {
            return cached;
        }

        try {
            List<ServiceStats> statsList = getServiceStats();
            List<ErrorAnalysis> errorList = new ArrayList<>();

            for (ServiceStats stats : statsList) {
                if (stats.getErrorSpans() > 0) {
                    ErrorAnalysis error = new ErrorAnalysis();
                    error.setServiceName(stats.getServiceName());
                    error.setTotalCalls(stats.getTotalSpans());
                    error.setErrorCalls(stats.getErrorSpans());
                    error.setErrorRate(stats.getErrorRate());
                    errorList.add(error);
                }
            }

            // 按错误率排序
            errorList.sort((a, b) -> Double.compare(b.getErrorRate(), a.getErrorRate()));

            putToCache(cacheKey, errorList);
            return errorList;

        } catch (Exception e) {
            log.error("获取错误分析失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取单个链路详情
     */
    public List<TraceSpan> getTraceDetail(String traceId) {
        try {
            // TODO: 实现从collector获取指定traceId的链路详情
            // 目前返回模拟数据
            return generateMockTraceSpans(traceId);
        } catch (Exception e) {
            log.error("获取链路详情失败: {}", traceId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        cache.clear();
        log.info("已清空数据缓存");
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return cache.size();
    }

    // ========== 辅助方法 ==========

    @SuppressWarnings("unchecked")
    private <T> T getFromCache(String key, Class<T> type) {
        CacheEntry<?> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.getData();
        }
        return null;
    }

    private <T> void putToCache(String key, T data) {
        cache.put(key, new CacheEntry<>(data));
    }

    // ========== 模型类 ==========

    @Data
    public static class CollectorStats {
        private long totalReceivedRequests;
        private long totalReceivedSpans;
        private long totalSuccessSpans;
        private long totalFailedSpans;
        private double successRate;
        private long runningHours;
        private Instant currentTime;
    }

    @Data
    public static class ServiceDependency {
        private String sourceService;
        private String targetService;
        private int callCount;
        private long totalDuration;
        private long avgDuration;
        private int errorCount;
        private double errorRate;
    }

    @Data
    public static class ServiceStats {
        private String serviceName;
        private int totalSpans;
        private int errorSpans;
        private long totalDuration;
        private long avgDuration;
        private double errorRate;
    }

    @Data
    public static class ErrorAnalysis {
        private String serviceName;
        private int totalCalls;
        private int errorCalls;
        private double errorRate;
    }

    @Data
    private static class CacheEntry<T> {
        private final T data;
        private final long timestamp;

        public CacheEntry(T data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    // ========== 模拟数据生成（过渡期间使用） ==========

    private List<TraceSpan> generateMockSpans(int count) {
        List<TraceSpan> spans = new ArrayList<>();
        String[] services = {"user-service", "order-service", "product-service", "payment-service", "inventory-service"};
        String[] operations = {
                "GET /api/users/{id}", "POST /api/orders", "GET /api/products",
                "PUT /api/products/{id}", "DELETE /api/users/{id}", "GET /api/orders/{id}"
        };

        Random random = new Random();
        for (int i = 0; i < count; i++) {
            TraceSpan span = new TraceSpan();
            span.setTraceId("trace-" + UUID.randomUUID().toString().substring(0, 8));
            span.setSpanId("span-" + UUID.randomUUID().toString().substring(0, 8));
            span.setServiceName(services[random.nextInt(services.length)]);
            span.setOperationName(operations[random.nextInt(operations.length)]);
            span.setStartTime(System.currentTimeMillis() - random.nextInt(3600000));
            span.setDurationMs(random.nextLong(500) + 50);
            span.setStatusCode(random.nextDouble() > 0.9 ? "ERROR" : "OK");
            span.setSpanKind("SERVER");
            span.setComponent("SpringMVC");

            // 随机生成远程服务调用
            if (random.nextDouble() > 0.5) {
                String remoteService;
                do {
                    remoteService = services[random.nextInt(services.length)];
                } while (remoteService.equals(span.getServiceName()));
                span.setRemoteService(remoteService);
            }

            spans.add(span);
        }

        return spans;
    }

    private List<TraceSpan> generateMockTraceSpans(String traceId) {
        List<TraceSpan> spans = new ArrayList<>();
        Random random = new Random();

        // 生成根Span
        TraceSpan rootSpan = new TraceSpan();
        rootSpan.setTraceId(traceId);
        rootSpan.setSpanId("span-root");
        rootSpan.setOperationName("GET /api/orders/123");
        rootSpan.setServiceName("order-service");
        rootSpan.setStartTime(System.currentTimeMillis() - 5000);
        rootSpan.setDurationMs(350L);
        rootSpan.setStatusCode("OK");
        rootSpan.setSpanKind("SERVER");
        spans.add(rootSpan);

        // 生成子Span
        String[] childServices = {"user-service", "product-service", "payment-service"};
        String[] childOperations = {
                "GET /api/users/456", "GET /api/products/789", "POST /api/payments"
        };

        long startOffset = 0;
        for (int i = 0; i < childServices.length; i++) {
            TraceSpan childSpan = new TraceSpan();
            childSpan.setTraceId(traceId);
            childSpan.setSpanId("span-child-" + i);
            childSpan.setParentSpanId("span-root");
            childSpan.setOperationName(childOperations[i]);
            childSpan.setServiceName(childServices[i]);
            childSpan.setStartTime(rootSpan.getStartTime() + startOffset);
            childSpan.setDurationMs(random.nextLong(200) + 50);
            childSpan.setStatusCode(i == 1 ? "ERROR" : "OK"); // 第二个子Span模拟错误
            childSpan.setSpanKind("CLIENT");
            childSpan.setRemoteService(childServices[i]);
            spans.add(childSpan);

            startOffset += 100;
        }

        return spans;
    }
}

/**
 * RestTemplate错误处理器
 */
class RestTemplateErrorHandler implements ResponseErrorHandler {
    @Override
    public boolean hasError(ClientHttpResponse response) {
        return false; // 不抛出异常，让调用方处理
    }

    @Override
    public void handleError(ClientHttpResponse response) {
        // 空实现
    }
}
