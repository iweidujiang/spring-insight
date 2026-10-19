package io.github.iweidujiang.springinsight.server.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 飞书 Interactive 卡片与可选加签。
 *
 * @author 苏渡苇
 * @since 2026-10-19
 */
class FeishuAlertChannelSenderTest {

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
     * 签名与飞书算法一致：时间戳和密钥拼成 HMAC 密钥，对空内容计算后 Base64。
     */
    @Test
    void signUsesTimestampAndSecretAsKey() throws Exception {
        long timestamp = 1_599_360_473L;
        String secret = "SEC-test";
        String expected = hmac(timestamp + "\n" + secret);
        assertEquals(expected, FeishuAlertChannelSender.sign(timestamp, secret));
    }

    /**
     * 带密钥时请求体含 sign、timestamp 和红色卡片；详情链接指向控制台。
     */
    @Test
    void sendPostsInteractiveCardWithSign() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        int port = startServer(body);
        String secret = "SEC-test";
        new FeishuAlertChannelSender(new ObjectMapper()).send(
                new AlertPayload(sampleFields()),
                new ChannelConfig("http://127.0.0.1:" + port + "/hook", properties(), secret));

        JsonNode root = new ObjectMapper().readTree(body.get());
        assertEquals("interactive", root.get("msg_type").asText());
        long timestamp = root.get("timestamp").asLong();
        assertEquals(FeishuAlertChannelSender.sign(timestamp, secret), root.get("sign").asText());
        assertTrue(timestamp < 10_000_000_000L);
        JsonNode card = root.get("card");
        assertEquals("Spring Insight 告警", card.get("header").get("title").get("content").asText());
        assertEquals("red", card.get("header").get("template").asText());
        String markdown = card.get("elements").get(0).get("content").asText();
        assertTrue(markdown.contains("**服务**: sca-order"));
        assertTrue(markdown.contains("**当前值**: 50.0"));
        JsonNode button = card.get("elements").get(1).get("actions").get(0);
        assertEquals("查看详情", button.get("text").get("content").asText());
        assertEquals("primary", button.get("type").asText());
        assertEquals("http://127.0.0.1:9966/traces?service=sca-order", button.get("url").asText());
    }

    /**
     * 未配置密钥时不写 sign 和 timestamp。
     */
    @Test
    void sendOmitsSignWhenSecretBlank() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        int port = startServer(body);
        new FeishuAlertChannelSender(new ObjectMapper()).send(
                new AlertPayload(sampleFields()),
                new ChannelConfig("http://127.0.0.1:" + port + "/hook", properties(), ""));
        JsonNode root = new ObjectMapper().readTree(body.get());
        assertFalse(root.has("sign"));
        assertFalse(root.has("timestamp"));
        assertEquals("interactive", root.get("msg_type").asText());
    }

    /**
     * @param body 收到的请求体
     * @return 监听端口
     * @throws Exception 启动失败
     */
    private int startServer(AtomicReference<String> body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = "{\"code\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
        });
        server.start();
        return server.getAddress().getPort();
    }

    /**
     * @return 带控制台地址的告警配置
     */
    private static InsightServerAlertProperties properties() {
        InsightServerAlertProperties properties = new InsightServerAlertProperties();
        InsightServerAlertProperties.Feishu feishu = new InsightServerAlertProperties.Feishu();
        feishu.setServerUrl("http://127.0.0.1:9966/");
        properties.setFeishu(feishu);
        return properties;
    }

    /**
     * @return 一条错误率告警字段
     */
    private static Map<String, Object> sampleFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("serviceName", "sca-order");
        fields.put("metric", "error_rate");
        fields.put("value", 50.0);
        fields.put("threshold", 10.0);
        fields.put("at", "2026-10-19T01:00:00Z");
        return fields;
    }

    /**
     * @param key HmacSHA256 密钥
     * @return 对空内容的 Base64 签名
     * @throws Exception 算法不可用
     */
    private static String hmac(String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(new byte[0]));
    }
}
