package io.github.iweidujiang.springinsight.demo.controller;

import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 测试控制器
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {

    private final List<String> products = Arrays.asList(
            "笔记本电脑", "智能手机", "平板电脑", "无线耳机",
            "智能手表", "游戏机", "数码相机", "蓝牙音箱"
    );

    @GetMapping("/trace")
    public Map<String, Object> testTrace() {
        log.info("测试追踪端点被调用");

        Map<String, Object> result = new HashMap<>();

        // 测试1：检查TraceContext是否可用
        try {
            TraceSpan span = TraceContext.startSpan("测试手动创建Span");
            result.put("traceId", span.getTraceId());
            result.put("spanId", span.getSpanId());

            Thread.sleep(100); // 模拟处理

            TraceContext.endSpan();
            result.put("traceSuccess", true);
            result.put("message", "TraceContext 正常工作");

        } catch (Exception e) {
            result.put("traceSuccess", false);
            result.put("error", e.getMessage());
            log.error("TraceContext 测试失败", e);
        }

        // 测试2：返回当前类加载器信息
        result.put("agentClassLoaded", checkIfAgentClassLoaded());
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }

    @GetMapping("/config")
    public Map<String, Object> showConfig() {
        Map<String, Object> config = new HashMap<>();

        // 显示所有相关配置
        config.put("java.version", System.getProperty("java.version"));
        config.put("spring.profiles.active", System.getProperty("spring.profiles.active"));

        // 检查配置属性文件
        config.put("configCheck", "检查 application.yml 中的 spring.insight 配置");

        return config;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "demo-service",
                "timestamp", new Date()
        );
    }

    @GetMapping("/users/{id}")
    public Map<String, Object> getUser(@PathVariable("id") String id) {
        log.info("查询用户信息: userId={}", id);

        // 模拟处理时间
        simulateProcessing(50, 150);

        // 模拟偶尔失败
        if ("0".equals(id)) {
            throw new RuntimeException("用户ID不能为0");
        }

        return Map.of(
                "id", id,
                "name", "用户" + id,
                "email", "user" + id + "@example.com",
                "age", ThreadLocalRandom.current().nextInt(18, 60)
        );
    }

    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestBody OrderRequest request) {
        log.info("创建订单: product={}, quantity={}", request.getProduct(), request.getQuantity());

        // 模拟处理时间
        simulateProcessing(100, 300);

        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("数量必须大于0");
        }

        String orderId = "ORD" + System.currentTimeMillis();

        return Map.of(
                "orderId", orderId,
                "product", request.getProduct(),
                "quantity", request.getQuantity(),
                "totalPrice", request.getQuantity() * getProductPrice(request.getProduct()),
                "status", "CREATED",
                "createdAt", new Date()
        );
    }

    @GetMapping("/products")
    public List<Map<String, Object>> getProducts() {
        log.info("获取产品列表");

        simulateProcessing(30, 100);

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String product = products.get(ThreadLocalRandom.current().nextInt(products.size()));
            result.add(Map.of(
                    "id", "P" + (i + 1),
                    "name", product,
                    "price", getProductPrice(product),
                    "stock", ThreadLocalRandom.current().nextInt(10, 100)
            ));
        }

        return result;
    }

    @GetMapping("/products/{id}")
    public Map<String, Object> getProduct(@PathVariable String id) {
        log.info("获取产品详情: productId={}", id);

        simulateProcessing(20, 80);

        if ("P999".equals(id)) {
            throw new RuntimeException("产品不存在: " + id);
        }

        String product = products.get(Math.abs(id.hashCode()) % products.size());

        return Map.of(
                "id", id,
                "name", product,
                "price", getProductPrice(product),
                "description", "这是" + product + "的详细描述",
                "category", "电子产品",
                "rating", ThreadLocalRandom.current().nextDouble(3.5, 5.0)
        );
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String keyword) {
        log.info("搜索产品: keyword={}", keyword);

        simulateProcessing(80, 200);

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String product = products.get((keyword.hashCode() + i) % products.size());
            if (product.contains(keyword) || keyword.length() < 2) {
                results.add(Map.of(
                        "id", "S" + (i + 1),
                        "name", product,
                        "matchScore", ThreadLocalRandom.current().nextDouble(0.5, 1.0)
                ));
            }
        }

        return Map.of(
                "keyword", keyword,
                "totalResults", results.size(),
                "results", results
        );
    }

    @GetMapping("/slow")
    public Map<String, Object> slowEndpoint() {
        log.info("调用慢接口");

        // 模拟慢处理
        simulateProcessing(1000, 3000);

        return Map.of(
                "message", "这是一个慢接口",
                "processingTime", "1-3秒",
                "timestamp", new Date()
        );
    }

    @GetMapping("/error-test")
    public Map<String, Object> errorTest() {
        log.error("测试错误接口被调用");

        // 随机抛出异常
        if (ThreadLocalRandom.current().nextBoolean()) {
            throw new RuntimeException("随机错误测试: " + System.currentTimeMillis());
        }

        return Map.of("status", "幸运成功");
    }

    // 模拟数据库查询或其他处理时间
    private void simulateProcessing(int minMs, int maxMs) {
        try {
            int sleepTime = ThreadLocalRandom.current().nextInt(minMs, maxMs);
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private double getProductPrice(String product) {
        return ThreadLocalRandom.current().nextDouble(100, 5000);
    }

    // 内部类
    @Data
    static class OrderRequest {
        private String product;
        private int quantity;

        // 验证方法
        public void validate() {
            if (product == null || product.trim().isEmpty()) {
                throw new IllegalArgumentException("产品名称不能为空");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("数量必须大于0");
            }
        }
    }

    private boolean checkIfAgentClassLoaded() {
        try {
            Class.forName("io.github.iweidujiang.springinsight.agent.context.TraceContext");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
