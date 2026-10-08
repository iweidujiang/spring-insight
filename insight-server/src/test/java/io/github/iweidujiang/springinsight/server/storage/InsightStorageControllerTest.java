package io.github.iweidujiang.springinsight.server.storage;

import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存储清除 API 参数校验与摘要。
 *
 * @since 2026-09-20
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class InsightStorageControllerTest {

    private InsightStorageController controller;
    private InsightServerStorageProperties props;

    /**
     * 使用 memory 门面构造控制器。
     */
    @BeforeEach
    void setUp() {
        props = new InsightServerStorageProperties();
        props.setMaxSpans(500);
        TraceSpanPersistenceService persistence =
                new TraceSpanPersistenceService(new InMemorySpanStore(props, "memory"));
        controller = new InsightStorageController(persistence, props);
    }

    /**
     * 摘要含容量与保留策略字段。
     */
    @Test
    void summaryHasCapacityFields() {
        Map<String, Object> body = controller.summary();
        assertEquals("memory", body.get("mode"));
        assertEquals(0, body.get("stored"));
        assertEquals(500, body.get("max"));
        assertEquals(0, body.get("maxAgeHours"));
        assertFalse((Boolean) body.get("retentionEnabled"));
        assertEquals(0.0, ((Number) body.get("usageRatio")).doubleValue(), 1e-9);
        assertFalse(body.containsKey("filePath"));
        assertFalse(body.containsKey("sqlitePath"));
    }

    /**
     * 启用时间保留时 summary 暴露策略；file 模式带路径。
     */
    @Test
    void summaryExposesRetentionAndPath() {
        props.getRetention().setMaxAgeHours(48);
        props.setMode("file");
        props.setFilePath("./data/test-spans.json");
        TraceSpanPersistenceService persistence =
                new TraceSpanPersistenceService(new InMemorySpanStore(props, "file"));
        controller = new InsightStorageController(persistence, props);

        Map<String, Object> body = controller.summary();
        assertEquals("file", body.get("mode"));
        assertEquals(48, body.get("maxAgeHours"));
        assertTrue((Boolean) body.get("retentionEnabled"));
        assertEquals("./data/test-spans.json", body.get("filePath"));
    }

    /**
     * 非法 scope / 缺服务名返回 400。
     */
    @Test
    void clearValidatesRequest() {
        ResponseEntity<?> badScope = controller.clear(new InsightStorageController.ClearRequest("nope", null, null, null));
        assertEquals(400, badScope.getStatusCode().value());

        ResponseEntity<?> badService = controller.clear(
                new InsightStorageController.ClearRequest("service", null, null, "  "));
        assertEquals(400, badService.getStatusCode().value());

        ResponseEntity<?> ok = controller.clear(new InsightStorageController.ClearRequest("all", null, null, null));
        assertEquals(200, ok.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) ok.getBody();
        assertTrue(body.containsKey("deleted"));
        assertEquals(0, body.get("remaining"));
    }
}
