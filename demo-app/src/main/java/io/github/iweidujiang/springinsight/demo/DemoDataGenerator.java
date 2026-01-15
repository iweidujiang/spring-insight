package io.github.iweidujiang.springinsight.demo;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 模拟应用调用，生成测试数据
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/15
 * └───────────────────────────────────────────────
 */
@Slf4j
@Component
public class DemoDataGenerator implements CommandLineRunner {
    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();
    private final AtomicInteger requestId = new AtomicInteger(0);

    @Value("${spring-insight.collector.url:http://localhost:8082}")
    private String collectorUrl;

    private final List<String> demoServices = Arrays.asList(
            "user-service", "order-service", "product-service",
            "payment-service", "inventory-service", "notification-service"
    );

    private final List<String> demoOperations = Arrays.asList(
            "GET /api/users/{id}",
            "POST /api/users",
            "GET /api/orders",
            "POST /api/orders",
            "GET /api/products",
            "POST /api/products",
            "GET /api/payments/{id}",
            "POST /api/payments"
    );

    @Override
    public void run(String... args) {
        log.info("Demo数据生成器启动，collector地址: {}", collectorUrl);

        // 启动后先发送一些初始数据
        generateBatchData(20);
    }

    /**
     * 每30秒生成一批数据
     */
    @Scheduled(fixedDelay = 30000)
    public void generateRegularData() {
        int count = random.nextInt(5) + 3; // 3-7条数据
        generateBatchData(count);
        log.info("生成了 {} 条测试数据", count);
    }

    /**
     * 生成一批测试数据
     */
    private void generateBatchData(int count) {
        try {
            List<TraceSpan> batch = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                batch.add(generateTraceSpan());

                // 30%的概率生成子span
                if (random.nextDouble() < 0.3) {
                    batch.add(generateChildSpan(batch.get(batch.size() - 1)));
                }
            }

            sendToCollector(batch);

        } catch (Exception e) {
            log.error("生成测试数据失败", e);
        }
    }

    /**
     * 生成一个TraceSpan
     */
    private TraceSpan generateTraceSpan() {
        String traceId = "demo-trace-" + UUID.randomUUID().toString().substring(0, 8);
        String serviceName = demoServices.get(random.nextInt(demoServices.size()));
        String operationName = demoOperations.get(random.nextInt(demoOperations.size()));

        long startTime = System.currentTimeMillis() - random.nextInt(5000);
        long duration = random.nextInt(500) + 50;

        TraceSpan span = new TraceSpan();
        span.setTraceId(traceId);
        span.setSpanId("span-" + requestId.incrementAndGet());
        span.setServiceName(serviceName);
        span.setServiceInstance("demo-instance-" + random.nextInt(3));
        span.setOperationName(operationName);
        span.setStartTime(startTime);
        span.setEndTime(startTime + duration);
        span.setDurationMs(duration);
        span.setSpanKind("SERVER");
        span.setComponent("DemoApp");

        // 10%的概率生成错误
        if (random.nextDouble() < 0.1) {
            span.setStatusCode("ERROR");
            span.setErrorCode("DEMO_ERROR_" + random.nextInt(5));
            span.setErrorMessage("模拟错误: " + span.getErrorCode());
        } else {
            span.setStatusCode("OK");
        }

        // 添加标签
        span.addTag("http.method", operationName.startsWith("GET") ? "GET" : "POST");
        span.addTag("http.path", operationName.split(" ")[1]);
        span.addTag("demo.data", "true");
        span.addTag("user.id", "user-" + random.nextInt(1000));

        // 40%的概率调用其他服务
        if (random.nextDouble() < 0.4) {
            String targetService;
            do {
                targetService = demoServices.get(random.nextInt(demoServices.size()));
            } while (targetService.equals(serviceName));

            span.setRemoteService(targetService);
            span.setRemoteEndpoint(targetService + "-endpoint");
        }

        return span;
    }

    /**
     * 生成子Span
     */
    private TraceSpan generateChildSpan(TraceSpan parentSpan) {
        String operation = "内部处理";
        if (parentSpan.getRemoteService() != null) {
            operation = "调用" + parentSpan.getRemoteService();
        }

        long startTime = parentSpan.getStartTime() + random.nextInt(50);
        long duration = random.nextInt(200) + 20;

        TraceSpan span = new TraceSpan();
        span.setTraceId(parentSpan.getTraceId());
        span.setSpanId("span-child-" + requestId.incrementAndGet());
        span.setParentSpanId(parentSpan.getSpanId());
        span.setServiceName(parentSpan.getServiceName());
        span.setServiceInstance(parentSpan.getServiceInstance());
        span.setOperationName(operation);
        span.setStartTime(startTime);
        span.setEndTime(startTime + duration);
        span.setDurationMs(duration);
        span.setSpanKind("INTERNAL");
        span.setComponent("DemoApp");
        span.setStatusCode(parentSpan.getStatusCode());

        if (parentSpan.getErrorCode() != null) {
            span.setErrorCode(parentSpan.getErrorCode());
            span.setErrorMessage(parentSpan.getErrorMessage());
        }

        return span;
    }

    /**
     * 发送数据到collector
     */
    private void sendToCollector(List<TraceSpan> spans) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Demo-Data", "true");

            Map<String, Object> request = new HashMap<>();
            request.put("serviceName", "demo-app");
            request.put("serviceInstance", "localhost:9090");
            request.put("spans", spans);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String url = collectorUrl + "/api/v1/spans/batch";
            restTemplate.postForEntity(url, entity, String.class);

        } catch (Exception e) {
            log.error("发送数据到collector失败", e);
        }
    }
}
