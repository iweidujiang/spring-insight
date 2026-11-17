package io.github.iweidujiang.springinsight.server.alert;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 告警控制台深链。
 *
 * @author 苏渡苇
 * @since 2026-11-17
 */
class AlertConsoleLinksTest {

    /**
     * 错误类指标主链指向带 status=error 的链路页。
     */
    @Test
    void errorMetricPrimaryIsTraces() {
        Map<String, Object> links = AlertConsoleLinks.build(
                "http://127.0.0.1:9966/", "sca-order", "error_rate", 15);
        assertEquals(1, links.get("hours"));
        assertEquals("/traces?service=sca-order&status=error&hours=1", links.get("tracesPath"));
        assertEquals(links.get("tracesPath"), links.get("primaryPath"));
        assertEquals("http://127.0.0.1:9966/traces?service=sca-order&status=error&hours=1",
                links.get("primaryUrl"));
        assertTrue(String.valueOf(links.get("servicePath")).startsWith("/services/sca-order?hours=1"));
    }

    /**
     * 延迟类指标主链指向服务详情。
     */
    @Test
    void latencyMetricPrimaryIsService() {
        Map<String, Object> links = AlertConsoleLinks.build(
                "http://localhost:9966", "provider", "latency_p95", 90);
        assertEquals(2, links.get("hours"));
        assertEquals(links.get("servicePath"), links.get("primaryPath"));
        assertEquals("http://localhost:9966/services/provider?hours=2", links.get("primaryUrl"));
    }

    /**
     * 渠道根地址优先与 primaryPath 拼接。
     */
    @Test
    void primaryUrlUsesChannelBaseWithPath() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("serviceName", "sca-order");
        fields.put("metric", "error_rate");
        fields.put("windowMinutes", 15);
        fields.put("consoleLinks", AlertConsoleLinks.build("", "sca-order", "error_rate", 15));
        fields.put("primaryUrl", "http://stale/old");
        assertEquals(
                "http://127.0.0.1:9966/traces?service=sca-order&status=error&hours=1",
                AlertConsoleLinks.primaryUrl(fields, "http://127.0.0.1:9966/"));
    }
}
