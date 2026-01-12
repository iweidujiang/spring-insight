package io.github.iweidujiang.springinsight.ui.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 模拟数据服务 用于测试
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/12
 * └───────────────────────────────────────────────
 */
@Slf4j
@Service
public class MockDataService {
    private final Random random = new Random();

    /**
     * 生成模拟服务名称
     */
    public List<String> generateServiceNames() {
        return Arrays.asList(
                "user-service",
                "order-service",
                "product-service",
                "payment-service",
                "inventory-service",
                "notification-service"
        );
    }

    /**
     * 生成模拟依赖关系
     */
    public List<Map<String, Object>> generateDependencies(int hours) {
        List<Map<String, Object>> dependencies = new ArrayList<>();

        String[] services = {
                "user-service", "order-service", "product-service",
                "payment-service", "inventory-service"
        };

        // 生成随机的调用关系
        for (int i = 0; i < services.length; i++) {
            for (int j = 0; j < services.length; j++) {
                if (i != j && random.nextDouble() > 0.5) {
                    Map<String, Object> dep = new HashMap<>();
                    dep.put("source_service", services[i]);
                    dep.put("target_service", services[j]);
                    dep.put("call_count", random.nextInt(1000) + 100);
                    dep.put("avg_duration", random.nextDouble() * 200 + 50);
                    dep.put("error_rate", random.nextDouble() * 5);
                    dependencies.add(dep);
                }
            }
        }

        return dependencies;
    }

    /**
     * 生成模拟链路追踪
     */
    public List<Map<String, Object>> generateTraces(int count) {
        List<Map<String, Object>> traces = new ArrayList<>();
        String[] operations = {
                "GET /api/users/{id}",
                "POST /api/orders",
                "GET /api/products",
                "PUT /api/products/{id}",
                "DELETE /api/users/{id}",
                "GET /api/orders/{id}"
        };

        String[] services = {
                "user-service",
                "order-service",
                "product-service"
        };

        for (int i = 0; i < count; i++) {
            Map<String, Object> trace = new HashMap<>();
            trace.put("traceId", "trace-" + UUID.randomUUID().toString().substring(0, 8));
            trace.put("spanId", "span-" + UUID.randomUUID().toString().substring(0, 8));
            trace.put("operationName", operations[random.nextInt(operations.length)]);
            trace.put("serviceName", services[random.nextInt(services.length)]);
            trace.put("startTime", System.currentTimeMillis() - random.nextInt(3600000));
            trace.put("durationMs", random.nextInt(500) + 50L);
            trace.put("statusCode", random.nextDouble() > 0.9 ? "ERROR" : "OK");
            trace.put("errorCode", random.nextDouble() > 0.9 ? "INTERNAL_ERROR" : null);
            trace.put("errorMessage", random.nextDouble() > 0.9 ? "Internal server error" : null);

            // 添加标签
            Map<String, String> tags = new HashMap<>();
            tags.put("http.method", random.nextBoolean() ? "GET" : "POST");
            tags.put("http.path", "/api/test");
            tags.put("http.status_code", trace.get("statusCode").equals("ERROR") ? "500" : "200");
            trace.put("tags", tags);

            traces.add(trace);
        }

        return traces;
    }

    /**
     * 生成服务统计
     */
    public List<Map<String, Object>> generateServiceStats() {
        List<Map<String, Object>> stats = new ArrayList<>();
        String[] services = {
                "user-service",
                "order-service",
                "product-service",
                "payment-service",
                "inventory-service"
        };

        for (String service : services) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("service_name", service);
            stat.put("span_count", random.nextInt(5000) + 1000);
            stats.add(stat);
        }

        return stats;
    }

    /**
     * 生成错误分析
     */
    public List<Map<String, Object>> generateErrorAnalysis(int hours) {
        List<Map<String, Object>> errors = new ArrayList<>();

        Map<String, Object> error1 = new HashMap<>();
        error1.put("service_name", "user-service");
        error1.put("total_calls", 2456);
        error1.put("error_calls", 123);
        error1.put("error_rate", 5.01);

        Map<String, Object> error2 = new HashMap<>();
        error2.put("service_name", "payment-service");
        error2.put("total_calls", 1890);
        error2.put("error_calls", 85);
        error2.put("error_rate", 4.50);

        errors.add(error1);
        errors.add(error2);

        return errors;
    }
}
