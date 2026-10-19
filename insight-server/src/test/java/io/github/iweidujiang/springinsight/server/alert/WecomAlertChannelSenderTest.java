package io.github.iweidujiang.springinsight.server.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 企业微信 Markdown 告警请求体。
 *
 * @author 苏渡苇
 * @since 2026-10-19
 */
class WecomAlertChannelSenderTest {

    private HttpServer server;

    /**
     * 关掉本地 HTTP 服务。
     */
    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 直接 POST Markdown，请求体不含签名字段。
     */
    @Test
    void sendPostsMarkdownWithoutSign() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = "{\"errcode\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
        });
        server.start();

        InsightServerAlertProperties properties = new InsightServerAlertProperties();
        InsightServerAlertProperties.Wecom wecom = new InsightServerAlertProperties.Wecom();
        wecom.setServerUrl("http://127.0.0.1:9966/");
        properties.setWecom(wecom);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("serviceName", "sca-order");
        fields.put("metric", "error_rate");
        fields.put("value", 50.0);
        fields.put("threshold", 10.0);
        fields.put("at", "2026-10-19T01:00:00Z");

        new WecomAlertChannelSender(new ObjectMapper()).send(
                new AlertPayload(fields),
                new ChannelConfig("http://127.0.0.1:" + server.getAddress().getPort() + "/hook", properties, ""));

        JsonNode root = new ObjectMapper().readTree(body.get());
        assertEquals("markdown", root.get("msgtype").asText());
        assertFalse(root.has("sign"));
        String content = root.get("markdown").get("content").asText();
        assertTrue(content.startsWith("## Spring Insight 告警"));
        assertTrue(content.contains("<font color=\"warning\">sca-order</font>"));
        assertTrue(content.contains("> 指标: error_rate"));
        assertTrue(content.contains("<font color=\"info\">50.0</font>"));
        assertTrue(content.contains("> 阈值: 10.0"));
        assertTrue(content.contains("> 时间: 2026-10-19T01:00:00Z"));
        assertTrue(content.contains("[查看详情](http://127.0.0.1:9966/traces?service=sca-order)"));
    }
}
