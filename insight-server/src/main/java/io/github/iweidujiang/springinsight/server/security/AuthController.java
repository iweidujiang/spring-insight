package io.github.iweidujiang.springinsight.server.security;

import io.github.iweidujiang.springinsight.server.config.InsightServerSecurityProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 控制台登录 / 鉴权状态（不经 UI Filter 保护）。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final InsightServerSecurityProperties securityProperties;
    private final UiSessionStore sessionStore;

    /**
     * @param securityProperties 安全配置
     * @param sessionStore       会话表
     */
    public AuthController(InsightServerSecurityProperties securityProperties, UiSessionStore sessionStore) {
        this.securityProperties = securityProperties;
        this.sessionStore = sessionStore;
    }

    /**
     * 返回当前鉴权开关状态，供前端决定是否跳转登录页。
     *
     * @return status JSON
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uiAuthEnabled", securityProperties.isUiAuthEnabled());
        body.put("ingestAuthEnabled", securityProperties.isIngestAuthActive());
        return ResponseEntity.ok(body);
    }

    /**
     * UI 登录：校验用户名密码并返回会话 token。
     *
     * @param request 登录体
     * @return token 或 401
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!securityProperties.isUiAuthEnabled()) {
            body.put("success", true);
            body.put("uiAuthEnabled", false);
            body.put("message", "ui auth disabled");
            return ResponseEntity.ok(body);
        }
        if (!StringUtils.hasText(securityProperties.normalizedUiPassword())) {
            body.put("success", false);
            body.put("message", "ui-password not configured");
            return ResponseEntity.badRequest().body(body);
        }
        String token = sessionStore.login(
                request != null ? request.username() : null,
                request != null ? request.password() : null);
        if (token == null) {
            body.put("success", false);
            body.put("message", "invalid username or password");
            return ResponseEntity.status(401).body(body);
        }
        body.put("success", true);
        body.put("token", token);
        body.put("tokenType", "Bearer");
        return ResponseEntity.ok(body);
    }

    /**
     * 注销当前会话。
     *
     * @param authorization Authorization Bearer
     * @param uiToken       X-Insight-Ui-Token
     * @return 结果
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = InsightTokenSupport.HEADER_UI_TOKEN, required = false) String uiToken) {
        String token = StringUtils.hasText(uiToken) ? uiToken.trim() : "";
        if (!StringUtils.hasText(token) && StringUtils.hasText(authorization)
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = authorization.substring(7).trim();
        }
        sessionStore.logout(token);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 登录请求体。
     *
     * @param username 用户名
     * @param password 密码
     */
    public record LoginRequest(String username, String password) {
    }
}
