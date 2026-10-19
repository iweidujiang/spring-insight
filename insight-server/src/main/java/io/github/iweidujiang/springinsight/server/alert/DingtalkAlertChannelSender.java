package io.github.iweidujiang.springinsight.server.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉群机器人：ActionCard，Webhook 使用 HmacSHA256 加签。
 */
@Slf4j
@Component
public class DingtalkAlertChannelSender implements InsightAlertChannelSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DingtalkAlertChannelSender(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    DingtalkAlertChannelSender(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String channelType() {
        return "dingtalk";
    }

    @Override
    public void send(AlertPayload payload, ChannelConfig config) throws Exception {
        String webhook = config.webhookUrl() == null ? "" : config.webhookUrl().trim();
        String secret = config.secret() == null ? "" : config.secret().trim();
        if (webhook.isEmpty() || secret.isEmpty()) {
            throw new IllegalStateException("dingtalk webhook or secret is empty");
        }
        long timestamp = System.currentTimeMillis();
        String url = signedUrl(webhook, timestamp, secret);
        String serverUrl = serverUrl(config.alertProperties());
        String json = objectMapper.writeValueAsString(actionCard(payload.fields(), serverUrl));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            log.warn("[告警] 钉钉非 2xx: status={}", status);
            throw new IllegalStateException("HTTP " + status);
        }
        if (dingtalkRejected(response.body())) {
            log.warn("[告警] 钉钉返回错误: {}", truncate(response.body()));
            throw new IllegalStateException("dingtalk errcode");
        }
    }

    /**
     * 钉钉加签：{@code Base64(HmacSHA256(timestamp + "\n" + secret))} 后再做 URL 编码。
     */
    static String sign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String base64 = Base64.getEncoder().encodeToString(raw);
        return URLEncoder.encode(base64, StandardCharsets.UTF_8);
    }

    static String signedUrl(String webhookUrl, long timestamp, String secret) throws Exception {
        String joiner = webhookUrl.contains("?") ? "&" : "?";
        return webhookUrl + joiner + "timestamp=" + timestamp + "&sign=" + sign(timestamp, secret);
    }

    static Map<String, Object> actionCard(Map<String, Object> fields, String serverUrl) {
        String service = text(fields.get("serviceName"), "unknown");
        String metric = text(fields.get("metric"), "");
        String value = text(fields.get("value"), "");
        String threshold = text(fields.get("threshold"), "");
        String time = text(fields.get("at"), "");
        String title = "Spring Insight 告警 - " + service;
        String text = "### " + title + "\n\n"
                + "- 服务: " + service + "\n"
                + "- 指标: " + metric + "\n"
                + "- 当前值: " + value + "\n"
                + "- 阈值: " + threshold + "\n"
                + "- 时间: " + time;
        String base = serverUrl == null ? "" : serverUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String actionUrl = base + "/traces?service="
                + URLEncoder.encode(service, StandardCharsets.UTF_8)
                + "&status=ERROR";
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("title", "查看详情");
        button.put("actionURL", actionUrl);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("title", title);
        card.put("text", text);
        card.put("btnOrientation", "0");
        card.put("btns", List.of(button));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "actionCard");
        body.put("actionCard", card);
        return body;
    }

    private static String serverUrl(InsightServerAlertProperties properties) {
        if (properties == null || properties.getDingtalk() == null || properties.getDingtalk().getServerUrl() == null) {
            return "";
        }
        return properties.getDingtalk().getServerUrl();
    }

    private boolean dingtalkRejected(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return false;
        }
        JsonNode err = objectMapper.readTree(body).get("errcode");
        if (err == null || err.isNull()) {
            return false;
        }
        if (err.isNumber()) {
            return err.intValue() != 0;
        }
        return !"0".equals(err.asText());
    }

    private static String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? fallback : s;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > 300 ? t.substring(0, 300) + "…" : t;
    }
}
