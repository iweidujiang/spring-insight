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
 * 飞书群机器人：Interactive 卡片。配置了加签密钥时，把签名和秒级时间戳放进请求体。
 *
 * @author 苏渡苇
 * @since 2026-10-19
 */
@Slf4j
@Component
public class FeishuAlertChannelSender implements InsightAlertChannelSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * 只保留这一个构造器，避免 Spring 因多个构造器去找无参构造。
     *
     * @param objectMapper JSON 序列化
     */
    public FeishuAlertChannelSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /**
     * @return 渠道标识 {@code feishu}
     */
    @Override
    public String channelType() {
        return "feishu";
    }

    /**
     * 向飞书机器人 POST 一张告警卡片。未配置密钥时不带签名。
     *
     * @param payload 告警字段
     * @param config  Webhook 地址、可选密钥、控制台根地址
     * @throws Exception 地址为空、HTTP 非 2xx，或飞书返回非 0 状态码
     */
    @Override
    public void send(AlertPayload payload, ChannelConfig config) throws Exception {
        String webhook = config.webhookUrl() == null ? "" : config.webhookUrl().trim();
        if (webhook.isEmpty()) {
            throw new IllegalStateException("feishu webhook is empty");
        }
        String secret = config.secret() == null ? "" : config.secret().trim();
        long timestamp = System.currentTimeMillis() / 1000L;
        Map<String, Object> body = interactiveCard(payload.fields(), serverUrl(config.alertProperties()));
        if (!secret.isEmpty()) {
            body.put("timestamp", String.valueOf(timestamp));
            body.put("sign", sign(timestamp, secret));
        }
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            log.warn("[告警] 飞书非 2xx: status={}", status);
            throw new IllegalStateException("HTTP " + status);
        }
        if (feishuRejected(response.body())) {
            log.warn("[告警] 飞书返回错误: {}", truncate(response.body()));
            throw new IllegalStateException("feishu code");
        }
    }

    /**
     * 飞书加签：把 {@code timestamp + "\n" + secret} 当作 HmacSHA256 密钥，对空内容计算后做 Base64。
     * 时间戳单位是秒。结果放进 JSON，不再 URL 编码。
     *
     * @param timestampSeconds 秒级时间戳
     * @param secret           机器人加签密钥
     * @return Base64 签名
     * @throws Exception 算法不可用
     */
    static String sign(long timestampSeconds, String secret) throws Exception {
        String stringToSign = timestampSeconds + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(new byte[0]);
        return Base64.getEncoder().encodeToString(raw);
    }

    /**
     * 组装飞书 Interactive 卡片。
     *
     * @param fields    告警字段，读取 serviceName、metric、value、threshold、at
     * @param serverUrl 控制台根地址，用于「查看详情」
     * @return 请求体；调用方在需要时再放入 timestamp 与 sign
     */
    static Map<String, Object> interactiveCard(Map<String, Object> fields, String serverUrl) {
        String service = text(fields.get("serviceName"), "unknown");
        String metric = text(fields.get("metric"), "");
        String value = text(fields.get("value"), "");
        String threshold = text(fields.get("threshold"), "");
        String time = text(fields.get("at"), "");
        String content = "**服务**: " + service + "\n"
                + "**指标**: " + metric + "\n"
                + "**当前值**: " + value + "\n"
                + "**阈值**: " + threshold + "\n"
                + "**时间**: " + time;
        String base = serverUrl == null ? "" : serverUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/traces?service=" + URLEncoder.encode(service, StandardCharsets.UTF_8);

        Map<String, Object> title = new LinkedHashMap<>();
        title.put("tag", "plain_text");
        title.put("content", "Spring Insight 告警");
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("title", title);
        header.put("template", "red");

        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("tag", "markdown");
        markdown.put("content", content);

        Map<String, Object> buttonText = new LinkedHashMap<>();
        buttonText.put("tag", "plain_text");
        buttonText.put("content", "查看详情");
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("tag", "button");
        button.put("text", buttonText);
        button.put("url", url);
        button.put("type", "primary");
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("tag", "action");
        action.put("actions", List.of(button));

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", header);
        card.put("elements", List.of(markdown, action));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "interactive");
        body.put("card", card);
        return body;
    }

    /**
     * @param properties 告警配置
     * @return 飞书消息里使用的控制台根地址；未配置时为空串
     */
    private static String serverUrl(InsightServerAlertProperties properties) {
        if (properties == null || properties.getFeishu() == null || properties.getFeishu().getServerUrl() == null) {
            return "";
        }
        return properties.getFeishu().getServerUrl();
    }

    /**
     * 飞书成功时 code 或 StatusCode 为 0。两者都没有则只看 HTTP 状态。
     *
     * @param body 响应体
     * @return 明确返回非 0 时为 true
     * @throws Exception JSON 无法解析
     */
    private boolean feishuRejected(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return false;
        }
        JsonNode root = objectMapper.readTree(body);
        return nonzero(root.get("code")) || nonzero(root.get("StatusCode"));
    }

    /**
     * @param node 状态码节点
     * @return 节点存在且不是 0
     */
    private static boolean nonzero(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isNumber()) {
            return node.intValue() != 0;
        }
        return !"0".equals(node.asText());
    }

    /**
     * @param value    字段值
     * @param fallback 空值时的替身
     * @return 展示文本
     */
    private static String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? fallback : s;
    }

    /**
     * @param s 响应摘要
     * @return 截断后的日志文本
     */
    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > 300 ? t.substring(0, 300) + "…" : t;
    }
}
