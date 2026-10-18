package io.github.iweidujiang.springinsight.server.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 向配置的 Webhook URL POST 告警 JSON。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@Component
public class InsightAlertWebhookSender implements InsightAlertChannelSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * Spring 注入用唯一构造器：内部自建 {@link HttpClient}，避免多构造器导致无法装配。
     *
     * @param objectMapper JSON 序列化
     */
    public InsightAlertWebhookSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public String channelType() {
        return "webhook";
    }

    /**
     * 按渠道配置 POST 告警 JSON。非 2xx 或网络失败时抛出异常。
     */
    @Override
    public void send(AlertPayload payload, ChannelConfig config) throws Exception {
        PostResult result = postDetailed(config.webhookUrl(), payload.fields());
        if (!result.ok()) {
            throw new IllegalStateException(result.error().isBlank() ? "webhook send failed" : result.error());
        }
    }

    /**
     * @param url  Webhook 地址
     * @param body 已冻结字段的 payload
     * @return HTTP 2xx 为 true；网络或非 2xx 为 false
     */
    public boolean post(String url, Map<String, Object> body) {
        return postDetailed(url, body).ok();
    }

    /**
     * 带状态信息的 POST（供预览 / 排障）。
     *
     * @param url  Webhook 地址
     * @param body payload
     * @return 结果摘要
     */
    public PostResult postDetailed(String url, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return PostResult.ok(status, truncate(response.body()));
            }
            log.warn("[告警] webhook 非 2xx: status={}, url={}", status, url);
            return PostResult.fail(status, "HTTP " + status, truncate(response.body()));
        } catch (Exception e) {
            log.warn("[告警] webhook 发送失败: url={}, error={}", url, e.getMessage());
            return PostResult.fail(0, e.getMessage() == null ? "send failed" : e.getMessage(), "");
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > 500 ? t.substring(0, 500) + "…" : t;
    }

    /**
     * Webhook POST 结果。
     *
     * @param ok           是否 2xx
     * @param statusCode   HTTP 状态；网络失败为 0
     * @param error        失败原因
     * @param responseBody 响应体摘要（可空）
     */
    public record PostResult(boolean ok, int statusCode, String error, String responseBody) {
        static PostResult ok(int status, String body) {
            return new PostResult(true, status, "", body == null ? "" : body);
        }

        static PostResult fail(int status, String error, String body) {
            return new PostResult(false, status, error == null ? "" : error, body == null ? "" : body);
        }
    }
}
