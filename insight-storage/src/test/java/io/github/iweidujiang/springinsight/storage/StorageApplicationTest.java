package io.github.iweidujiang.springinsight.storage;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ┌───────────────────────────────────────────────┐
 * │ 📦 存储模块集成测试
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────┘
 */
@Slf4j
@SpringBootTest
public class StorageApplicationTest {

    @Autowired
    private TraceSpanPersistenceService traceSpanPersistenceService;

    @Test
    void testSaveAndQueryTraceSpan() {
        log.info("开始追踪链路跨度的保存与查询测试...");

        // 1. 创建测试 TraceSpan
        TraceSpan span = createTestTraceSpan();

        // 2. 保存到数据库
        log.info("将追踪链路跨度保存至数据库...");
        traceSpanPersistenceService.saveTraceSpan(span);
        log.info("追踪链路跨度保存成功");

        // 3. 查询验证
        log.info("根据ID查询追踪链路：{}", span.getTraceId());
        List<TraceSpan> trace = traceSpanPersistenceService.getTraceById(span.getTraceId());

        if (!trace.isEmpty()) {
            log.info("成功获取追踪链路，包含 {} 条链路跨度", trace.size());
            TraceSpan retrieved = trace.getFirst();
            log.info("获取到的链路跨度详情：spanId={}, 操作名称={}, 耗时={}毫秒",
                    retrieved.getSpanId(), retrieved.getOperationName(), retrieved.getDurationMs());
        } else {
            log.warn("未找到traceId为 {} 的追踪链路", span.getTraceId());
        }

        log.info("测试执行成功完成");
    }

    @Test
    void testBatchSaveTraceSpans() {
        log.info("开始批量保存测试...");

        // 创建多个 TraceSpan
        List<TraceSpan> spans = new ArrayList<>();
        String traceId = UUID.randomUUID().toString();

        for (int i = 0; i < 5; i++) {
            TraceSpan span = createTestTraceSpan();
            span.setTraceId(traceId);
            span.setSpanId(traceId + "-" + i);
            span.setOperationName("Test Operation " + i);
            spans.add(span);
        }

        // 批量保存
        log.info("批量保存 {} 条追踪链路跨度...", spans.size());
        traceSpanPersistenceService.saveTraceSpans(spans);
        log.info("批量保存完成");

        // 查询验证
        List<TraceSpan> retrieved = traceSpanPersistenceService.getTraceById(traceId);
        log.info("为traceId {} 获取到 {} 条链路跨度", traceId, retrieved.size());

        log.info("批量保存测试执行成功完成");
    }

    @Test
    void testServiceDependencies() {
        log.info("测试服务依赖关系查询...");

        // 先确保有一些数据
        testBatchSaveTraceSpans();

        // 查询服务依赖
        List<Map<String, Object>> dependencies = traceSpanPersistenceService.getServiceDependencies(24);
        log.info("查询到 {} 条服务依赖关系", dependencies.size());

        for (Map<String, Object> dep : dependencies) {
            log.info("依赖关系：{} -> {}, 调用次数：{}, 平均耗时：{}",
                    dep.get("source_service"),
                    dep.get("target_service"),
                    dep.get("call_count"),
                    dep.get("avg_duration"));
        }

        log.info("服务依赖关系测试执行完成");
    }

    private TraceSpan createTestTraceSpan() {
        long startTime = System.currentTimeMillis() - 1000;
        long endTime = System.currentTimeMillis();

        TraceSpan span = new TraceSpan();
        span.setTraceId(UUID.randomUUID().toString());
        span.setSpanId(UUID.randomUUID().toString());
        span.setServiceName("test-service");
        span.setServiceInstance("localhost:8080");
        span.setHostIp("127.0.0.1");
        span.setHostPort(8080);
        span.setOperationName("GET /api/test");
        span.setSpanKind("SERVER");
        span.setComponent("SpringMVC");
        span.setEndpoint("com.example.TestController.testMethod");
        span.setStartTime(startTime);
        span.setEndTime(endTime);
        span.setDurationMs(endTime - startTime);
        span.setStatusCode("OK");
        span.setRemoteService("user-service");
        span.setRemoteEndpoint("GET /api/users");

        // 添加一些标签
        span.getTags().put("http.method", "GET");
        span.getTags().put("http.path", "/api/test");
        span.getTags().put("http.status_code", "200");
        span.getTags().put("user.id", "12345");

        return span;
    }
}
