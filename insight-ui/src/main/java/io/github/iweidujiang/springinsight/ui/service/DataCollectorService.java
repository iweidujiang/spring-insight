package io.github.iweidujiang.springinsight.ui.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
        // 配置RestTemplate超时
        restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory());
        ((SimpleClientHttpRequestFactory) restTemplate.getRequestFactory()).setConnectTimeout(3000);
        ((SimpleClientHttpRequestFactory) restTemplate.getRequestFactory()).setReadTimeout(10000);
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
                Map<String, Object> data = objectMapper.readValue(response.getBody(), new TypeReference<>() {
                });

                CollectorStats stats = new CollectorStats();
                stats.setTotalReceivedRequests(((Number) data.get("totalReceivedRequests")).longValue());
                stats.setTotalReceivedSpans(((Number) data.get("totalReceivedSpans")).longValue());
                stats.setTotalSuccessSpans(((Number) data.get("totalSuccessSpans")).longValue());
                stats.setTotalFailedSpans(((Number) data.get("totalFailedSpans")).longValue());
                stats.setSuccessRate(((Number) data.get("successRate")).doubleValue());
                stats.setRunningHours(((Number) data.get("runningHours")).longValue());
                stats.setCurrentTime(Instant.parse((String) data.get("currentTime")));

                putToCache(cacheKey, stats);
                log.debug("获取collector统计成功");
                return stats;
            }
        } catch (Exception e) {
            log.error("获取collector统计失败: {}", e.getMessage());
        }

        return createDefaultCollectorStats();
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

        try {
            String url = collectorUrl + "/api/v1/ui/services";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<String> services = objectMapper.readValue(response.getBody(), new TypeReference<List<String>>() {});
                putToCache(cacheKey, services);
                log.debug("获取服务列表成功: {}", services);
                return services;
            }
        } catch (Exception e) {
            log.error("获取服务列表失败: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * 获取最近链路
     */
    public List<TraceSpan> getRecentSpans(int hours, int limit) {
        String cacheKey = "recent-spans-" + hours + "-" + limit;

        List<TraceSpan> cached = getFromCache(cacheKey, List.class);
        if (cached != null) {
            log.debug("从缓存获取最近链路: {}条", cached.size());
            return cached;
        }

        try {
            log.debug("开始获取最近链路，hours: {}, limit: {}", hours, limit);
            String url = collectorUrl + "/api/v1/ui/traces/recent?hours=" + hours + "&limit=" + limit;
            log.debug("请求URL: {}", url);
            
            // 设置连接超时和读取超时
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(5000);
            RestTemplate timeoutRestTemplate = new RestTemplate(factory);
            
            ResponseEntity<String> response = timeoutRestTemplate.getForEntity(url, String.class);
            log.debug("响应状态码: {}", response.getStatusCode());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("响应体长度: {}字符", response.getBody().length());
                // 限制响应体长度，避免处理过大的数据
                if (response.getBody().length() > 1000000) {
                    log.warn("响应体过大，超过1MB，返回空列表");
                    return Collections.emptyList();
                }
                List<TraceSpan> spans = objectMapper.readValue(response.getBody(), new TypeReference<List<TraceSpan>>() {});
                putToCache(cacheKey, spans);
                log.debug("获取最近链路成功: {}条", spans.size());
                return spans;
            }
        } catch (Exception e) {
            log.error("获取最近链路失败: {}", e.getMessage(), e);
        }

        log.debug("返回空链路列表");
        return Collections.emptyList();
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
            String url = collectorUrl + "/api/v1/ui/services/" + serviceName + "/traces?limit=" + limit;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<TraceSpan> spans = objectMapper.readValue(response.getBody(), new TypeReference<List<TraceSpan>>() {});
                putToCache(cacheKey, spans);
                log.debug("获取服务{}的链路成功: {}条", serviceName, spans.size());
                return spans;
            }
        } catch (Exception e) {
            log.error("获取服务{}的链路失败: {}", serviceName, e.getMessage());
        }

        return Collections.emptyList();
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
            String url = collectorUrl + "/api/v1/ui/dependencies?hours=" + hours;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> rawData = objectMapper.readValue(response.getBody(),
                        new TypeReference<List<Map<String, Object>>>() {});

                List<ServiceDependency> dependencies = new ArrayList<>();
                for (Map<String, Object> raw : rawData) {
                    ServiceDependency dep = new ServiceDependency();
                    dep.setSourceService((String) raw.get("source_service"));
                    dep.setTargetService((String) raw.get("target_service"));
                    dep.setCallCount(((Number) raw.get("call_count")).intValue());

                    Object avgDuration = raw.get("avg_duration");
                    if (avgDuration != null) {
                        dep.setAvgDuration(((Number) avgDuration).longValue());
                    }

                    dependencies.add(dep);
                }

                putToCache(cacheKey, dependencies);
                log.debug("获取服务依赖关系成功: {}条", dependencies.size());
                return dependencies;
            }
        } catch (Exception e) {
            log.error("获取服务依赖关系失败: {}", e.getMessage());
        }

        return Collections.emptyList();
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
            String url = collectorUrl + "/api/v1/ui/services/stats";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> rawData = objectMapper.readValue(response.getBody(),
                        new TypeReference<List<Map<String, Object>>>() {});

                List<ServiceStats> statsList = new ArrayList<>();
                for (Map<String, Object> raw : rawData) {
                    ServiceStats stats = new ServiceStats();
                    stats.setServiceName((String) raw.get("service_name"));
                    stats.setTotalSpans(((Number) raw.get("span_count")).intValue());
                    statsList.add(stats);
                }

                // 按总Span数排序
                statsList.sort((a, b) -> Integer.compare(b.getTotalSpans(), a.getTotalSpans()));

                putToCache(cacheKey, statsList);
                log.debug("获取服务统计成功: {}个服务", statsList.size());
                return statsList;
            }
        } catch (Exception e) {
            log.error("获取服务统计失败: {}", e.getMessage());
        }

        return Collections.emptyList();
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
            String url = collectorUrl + "/api/v1/ui/errors/analysis?hours=" + hours;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> rawData = objectMapper.readValue(response.getBody(),
                        new TypeReference<List<Map<String, Object>>>() {});

                List<ErrorAnalysis> errorList = new ArrayList<>();
                for (Map<String, Object> raw : rawData) {
                    ErrorAnalysis error = new ErrorAnalysis();
                    error.setServiceName((String) raw.get("service_name"));
                    error.setTotalCalls(((Number) raw.get("total_calls")).intValue());
                    error.setErrorCalls(((Number) raw.get("error_calls")).intValue());

                    Object errorRate = raw.get("error_rate");
                    if (errorRate != null) {
                        error.setErrorRate(((Number) errorRate).doubleValue());
                    } else if (error.getTotalCalls() > 0) {
                        error.setErrorRate((double) error.getErrorCalls() / error.getTotalCalls() * 100);
                    }

                    errorList.add(error);
                }

                // 按错误率排序
                errorList.sort((a, b) -> Double.compare(b.getErrorRate(), a.getErrorRate()));

                putToCache(cacheKey, errorList);
                log.debug("获取错误分析成功: {}个服务", errorList.size());
                return errorList;
            }
        } catch (Exception e) {
            log.error("获取错误分析失败: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * 获取单个链路详情
     */
    public List<TraceSpan> getTraceDetail(String traceId) {
        try {
            log.debug("开始获取traceId: {}的链路详情", traceId);
            
            // 先从所有数据中查找指定traceId的链路
            // 使用较小的limit值，避免处理过多数据
            List<TraceSpan> recentSpans = getRecentSpans(24, 100);
            log.debug("获取到最近链路: {}条", recentSpans.size());
            
            List<TraceSpan> traceSpans = new ArrayList<>();

            for (TraceSpan span : recentSpans) {
                if (span != null && traceId.equals(span.getTraceId())) {
                    traceSpans.add(span);
                }
            }

            if (!traceSpans.isEmpty()) {
                // 按开始时间排序
                traceSpans.sort(Comparator.comparing(TraceSpan::getStartTime));
                log.debug("找到traceId: {}的链路: {}条", traceId, traceSpans.size());
                return traceSpans;
            }

            log.debug("未找到traceId: {}的链路", traceId);
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("获取链路详情失败: {}", traceId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取实时统计
     */
    public Map<String, Object> getRealtimeStats() {
        try {
            String url = collectorUrl + "/api/v1/ui/stats/realtime";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.error("获取实时统计失败: {}", e.getMessage());
        }

        Map<String, Object> defaultStats = new HashMap<>();
        defaultStats.put("collectorStats", createDefaultCollectorStats());
        defaultStats.put("timestamp", Instant.now().toString());
        return defaultStats;
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

    private CollectorStats createDefaultCollectorStats() {
        CollectorStats stats = new CollectorStats();
        stats.setTotalReceivedRequests(0);
        stats.setTotalReceivedSpans(0);
        stats.setTotalSuccessSpans(0);
        stats.setTotalFailedSpans(0);
        stats.setSuccessRate(100.0);
        stats.setRunningHours(0);
        stats.setCurrentTime(Instant.now());
        return stats;
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
