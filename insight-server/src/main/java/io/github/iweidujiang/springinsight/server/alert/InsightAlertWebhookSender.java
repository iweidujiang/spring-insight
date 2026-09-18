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
public class InsightAlertWebhookSender {

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

    /**
     * @param url  Webhook 地址
     * @param body 已冻结字段的 payload
     * @return HTTP 2xx 为 true；网络或非 2xx 为 false
     */
    public boolean post(String url, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            // 仅 2xx 记成功，便于冷却只在真正送达后生效
            if (status >= 200 && status < 300) {
                return true;
            }
            log.warn("[告警] webhook 非 2xx: status={}, url={}", status, url);
            return false;
        } catch (Exception e) {
            log.warn("[告警] webhook 发送失败: url={}, error={}", url, e.getMessage());
            return false;
        }
    }
}
