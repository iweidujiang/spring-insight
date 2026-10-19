package io.github.iweidujiang.springinsight.server.settings;

import io.github.iweidujiang.springinsight.server.alert.AlertPayload;
import io.github.iweidujiang.springinsight.server.alert.ChannelConfig;
import io.github.iweidujiang.springinsight.server.alert.InsightAlertChannelSender;
import io.github.iweidujiang.springinsight.server.alert.InsightAlertEvaluator;
import io.github.iweidujiang.springinsight.server.alert.InsightAlertScheduler;
import io.github.iweidujiang.springinsight.server.alert.InsightAlertWebhookSender;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.List;
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
@Tag(name = "Settings", description = "运行时设置与 Webhook 预览")
public class InsightSettingsController {

    private final InsightRuntimeSettingsService settingsService;
    private final InsightAlertWebhookSender webhookSender;
    private final List<InsightAlertChannelSender> channelSenders;

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

    /**
     * 按渠道试发一条样本。密钥、令牌、SMTP 密码留空时用已保存的值。{@code send=false} 只返回样本。
     *
     * @param body 渠道草稿
     * @return 样本和发送结果
     * @since 2026-10-19
     */
    @PostMapping("/alert/channel-test")
    public ResponseEntity<?> channelTest(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        String type = text(request.get("type")).toLowerCase();
        boolean send = Boolean.TRUE.equals(request.get("send")) || "true".equalsIgnoreCase(text(request.get("send")));
        InsightRuntimeSettings.ChannelConfig draft = draftChannel(request);
        fillSavedSecrets(draft);
        InsightServerAlertProperties alert = settingsService.effectiveAlert();
        String at = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        Map<String, Object> payload = InsightAlertEvaluator.previewPayload(alert, at);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("payload", payload);
        resp.put("type", type);
        if (!send) {
            resp.put("dryRun", true);
            resp.put("sent", false);
            return ResponseEntity.ok(resp);
        }
        InsightAlertChannelSender sender = findSender(type);
        if (sender == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "不支持的通知渠道", "sent", false));
        }
        try {
            sender.send(new AlertPayload(payload), InsightAlertScheduler.channelConfig(alert, draft, type));
            resp.put("dryRun", false);
            resp.put("sent", true);
            resp.put("ok", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("dryRun", false);
            resp.put("sent", true);
            resp.put("ok", false);
            resp.put("error", e.getMessage() == null ? "发送失败" : e.getMessage());
            return ResponseEntity.ok(resp);
        }
    }

    /**
     * @param type 渠道类型
     * @return 已注册的发送器；没有时为 null
     */
    private InsightAlertChannelSender findSender(String type) {
        for (InsightAlertChannelSender sender : channelSenders) {
            if (type.equals(sender.channelType())) {
                return sender;
            }
        }
        return null;
    }

    /**
     * 草稿里的密钥留空时，沿用同一 id 已保存的值。
     */
    private void fillSavedSecrets(InsightRuntimeSettings.ChannelConfig draft) {
        for (InsightRuntimeSettings.ChannelConfig saved : settingsService.effectiveChannels()) {
            if (saved.getId() != null && saved.getId().equals(draft.getId())) {
                if (!StringUtils.hasText(draft.getSecret())) {
                    draft.setSecret(saved.getSecret());
                }
                if (!StringUtils.hasText(draft.getToken())) {
                    draft.setToken(saved.getToken());
                }
                if (!StringUtils.hasText(draft.getSmtpPassword())) {
                    draft.setSmtpPassword(saved.getSmtpPassword());
                }
                return;
            }
        }
    }

    /**
     * @param request 试发请求
     * @return 渠道草稿
     */
    private static InsightRuntimeSettings.ChannelConfig draftChannel(Map<String, Object> request) {
        InsightRuntimeSettings.ChannelConfig channel = new InsightRuntimeSettings.ChannelConfig();
        channel.setId(text(request.get("id")));
        channel.setType(text(request.get("type")).toLowerCase());
        channel.setEnabled(true);
        channel.setWebhookUrl(text(request.get("webhookUrl")));
        channel.setSecret(text(request.get("secret")));
        channel.setToken(text(request.get("token")));
        channel.setServerUrl(text(request.get("serverUrl")));
        channel.setSmtpHost(text(request.get("smtpHost")));
        channel.setSmtpPort(number(request.get("smtpPort"), 587));
        channel.setSmtpUsername(text(request.get("smtpUsername")));
        channel.setSmtpPassword(text(request.get("smtpPassword")));
        channel.setSmtpFrom(text(request.get("smtpFrom")));
        channel.setSmtpTo(text(request.get("smtpTo")));
        channel.setStartTls(!Boolean.FALSE.equals(request.get("startTls")));
        channel.setSsl(Boolean.TRUE.equals(request.get("ssl")));
        return channel;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
