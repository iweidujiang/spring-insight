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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉钉 ActionCard 与加签参数。
 */
class DingtalkAlertChannelSenderTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void signMatchesHmacSha256() throws Exception {
        long timestamp = 1_700_000_000_000L;
        String secret = "SEC-test";
        String signed = DingtalkAlertChannelSender.sign(timestamp, secret);
        String expected = URLDecoder.decode(signed, StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal((timestamp + "\n" + secret).getBytes(StandardCharsets.UTF_8));
        assertEquals(Base64.getEncoder().encodeToString(raw), expected);
        assertTrue(DingtalkAlertChannelSender.signedUrl(
                "https://oapi.dingtalk.com/robot/send?access_token=abc", timestamp, secret)
                .contains("&timestamp=" + timestamp + "&sign="));
    }

    @Test
    void sendPostsActionCardWithSignature() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/robot/send", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = "{\"errcode\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        InsightServerAlertProperties properties = new InsightServerAlertProperties();
        InsightServerAlertProperties.Dingtalk dingtalk = new InsightServerAlertProperties.Dingtalk();
        dingtalk.setServerUrl("http://127.0.0.1:9966/");
        properties.setDingtalk(dingtalk);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("serviceName", "sca-order");
        fields.put("metric", "error_rate");
        fields.put("value", 50.0);
        fields.put("threshold", 10.0);
        fields.put("at", "2026-10-19T01:00:00Z");

        String secret = "SEC-test";
        String webhook = "http://127.0.0.1:" + port + "/robot/send?access_token=abc";
        new DingtalkAlertChannelSender(new ObjectMapper()).send(
                new AlertPayload(fields),
                new ChannelConfig(webhook, properties, secret));

        String query = rawQuery.get();
        assertTrue(query.startsWith("access_token=abc&timestamp="));
        int tsAt = query.indexOf("timestamp=") + "timestamp=".length();
        String timestamp = query.substring(tsAt, query.indexOf('&', tsAt));
        assertTrue(query.contains("&sign=" + DingtalkAlertChannelSender.sign(Long.parseLong(timestamp), secret)));

        JsonNode card = new ObjectMapper().readTree(body.get());
        assertEquals("actionCard", card.get("msgtype").asText());
        assertEquals("Spring Insight 告警 - sca-order", card.get("actionCard").get("title").asText());
        assertTrue(card.get("actionCard").get("text").asText().contains("- 指标: error_rate"));
        assertTrue(card.get("actionCard").get("text").asText().contains("- 当前值: 50.0"));
        assertEquals("0", card.get("actionCard").get("btnOrientation").asText());
        assertEquals("查看详情", card.get("actionCard").get("btns").get(0).get("title").asText());
        assertEquals("http://127.0.0.1:9966/traces?service=sca-order&status=ERROR",
                card.get("actionCard").get("btns").get(0).get("actionURL").asText());
    }
}
