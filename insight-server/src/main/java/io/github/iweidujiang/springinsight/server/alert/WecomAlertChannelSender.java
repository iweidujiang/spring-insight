package io.github.iweidujiang.springinsight.server.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信群机器人：Markdown 消息，Webhook 无需加签。
 *
 * @author 苏渡苇
 * @since 2026-10-19
 */
@Slf4j
@Component
public class WecomAlertChannelSender implements InsightAlertChannelSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * 只保留这一个构造器，避免 Spring 因多个构造器去找无参构造。
     *
     * @param objectMapper JSON 序列化
     */
    public WecomAlertChannelSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /**
     * @return 渠道标识 {@code wecom}
     */
    @Override
    public String channelType() {
        return "wecom";
    }

    /**
     * 向企业微信机器人 POST 一条 Markdown 告警。
     *
     * @param payload 告警字段
     * @param config  Webhook 地址和控制台根地址
     * @throws Exception 地址为空、HTTP 非 2xx，或 errcode 非 0
     */
    @Override
    public void send(AlertPayload payload, ChannelConfig config) throws Exception {
        String webhook = config.webhookUrl() == null ? "" : config.webhookUrl().trim();
        if (webhook.isEmpty()) {
            throw new IllegalStateException("wecom webhook is empty");
        }
        String json = objectMapper.writeValueAsString(
                markdown(payload.fields(), serverUrl(config.alertProperties())));
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            log.warn("[告警] 企业微信非 2xx: status={}", status);
            throw new IllegalStateException("HTTP " + status);
        }
        if (wecomRejected(response.body())) {
            log.warn("[告警] 企业微信返回错误: {}", truncate(response.body()));
            throw new IllegalStateException("wecom errcode");
        }
    }

    /**
     * 组装企业微信 Markdown 消息。
     *
     * @param fields    告警字段，读取 serviceName、metric、value、threshold、at
     * @param serverUrl 控制台根地址，用于「查看详情」
     * @return 请求体
     */
    static Map<String, Object> markdown(Map<String, Object> fields, String serverUrl) {
        String service = text(fields.get("serviceName"), "unknown");
        String metric = text(fields.get("metric"), "");
        String value = text(fields.get("value"), "");
        String threshold = text(fields.get("threshold"), "");
        String time = text(fields.get("at"), "");
        String base = serverUrl == null ? "" : serverUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/traces?service=" + URLEncoder.encode(service, StandardCharsets.UTF_8);
        String content = "## Spring Insight 告警\n"
                + "> 服务: <font color=\"warning\">" + service + "</font>\n"
                + "> 指标: " + metric + "\n"
                + "> 当前值: <font color=\"info\">" + value + "</font>\n"
                + "> 阈值: " + threshold + "\n"
                + "> 时间: " + time + "\n\n"
                + "[查看详情](" + url + ")";
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("content", content);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", markdown);
        return body;
    }

    /**
     * @param properties 告警配置
     * @return 消息里使用的控制台根地址；未配置时为空串
     */
    private static String serverUrl(InsightServerAlertProperties properties) {
        if (properties == null || properties.getWecom() == null || properties.getWecom().getServerUrl() == null) {
            return "";
        }
        return properties.getWecom().getServerUrl();
    }

    /**
     * 企业微信成功时 errcode 为 0。没有该字段时只看 HTTP 状态。
     *
     * @param body 响应体
     * @return errcode 存在且不是 0
     * @throws Exception JSON 无法解析
     */
    private boolean wecomRejected(String body) throws Exception {
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
