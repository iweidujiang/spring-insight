package io.github.iweidujiang.springinsight.server.settings;

import io.github.iweidujiang.springinsight.server.alert.InsightAlertEvaluator;
import io.github.iweidujiang.springinsight.server.alert.InsightAlertWebhookSender;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 控制台运行时设置 API（告警 / AI；保存后立即生效）。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ui/settings")
@RequiredArgsConstructor
public class InsightSettingsController {

    private final InsightRuntimeSettingsService settingsService;
    private final InsightAlertWebhookSender webhookSender;

    /**
     * @return 脱敏后的当前设置
     */
    @GetMapping
    public Map<String, Object> get() {
        return settingsService.publicView();
    }

    /**
     * 保存并生效；密钥字段留空表示不修改。
     *
     * @param body 请求体
     * @return 保存后的脱敏视图
     */
    @PutMapping
    public ResponseEntity<?> put(@RequestBody InsightRuntimeSettings body) {
        try {
            return ResponseEntity.ok(settingsService.updateAndApply(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.warn("[设置] 保存失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("message", "保存失败: " + e.getMessage()));
        }
    }

    /**
     * Webhook 预览：默认只返回样本 payload；{@code send=true} 时真正 POST 一次（不写冷却）。
     * <p>
     * 请求体可选字段：{@code send}（boolean）、{@code webhookUrl}（覆盖当前生效 URL）。
     * </p>
     *
     * @param body 可选请求体
     * @return payload + dryRun / 发送结果
     */
    @PostMapping("/alert/webhook-preview")
    public ResponseEntity<?> webhookPreview(@RequestBody(required = false) Map<String, Object> body) {
        InsightServerAlertProperties alert = settingsService.effectiveAlert();
        String url = alert.normalizedWebhookUrl();
        boolean send = false;
        if (body != null) {
            Object sendObj = body.get("send");
            send = Boolean.TRUE.equals(sendObj) || "true".equalsIgnoreCase(String.valueOf(sendObj));
            Object urlObj = body.get("webhookUrl");
            if (urlObj != null && StringUtils.hasText(String.valueOf(urlObj))) {
                url = String.valueOf(urlObj).trim();
            }
        }

        String at = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        Map<String, Object> payload = InsightAlertEvaluator.previewPayload(alert, at);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("payload", payload);
        resp.put("webhookUrl", url == null ? "" : url);

        if (!send) {
            resp.put("dryRun", true);
            resp.put("sent", false);
            return ResponseEntity.ok(resp);
        }

        if (!StringUtils.hasText(url)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "请先填写 Webhook URL",
                    "payload", payload,
                    "dryRun", false,
                    "sent", false));
        }

        InsightAlertWebhookSender.PostResult result = webhookSender.postDetailed(url, payload);
        resp.put("dryRun", false);
        resp.put("sent", true);
        resp.put("ok", result.ok());
        resp.put("statusCode", result.statusCode());
        if (StringUtils.hasText(result.error())) {
            resp.put("error", result.error());
        }
        if (StringUtils.hasText(result.responseBody())) {
            resp.put("responseBody", result.responseBody());
        }
        return ResponseEntity.ok(resp);
    }
}
