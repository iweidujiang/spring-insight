package io.github.iweidujiang.springinsight.server.security;

import io.github.iweidujiang.springinsight.server.config.InsightServerSecurityProperties;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 UI 会话表（单机 Server；默认关闭 UI 鉴权时不使用）。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public class UiSessionStore {

    private final InsightServerSecurityProperties securityProperties;
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    /**
     * @param securityProperties 安全配置（含 TTL）
     */
    public UiSessionStore(InsightServerSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /**
     * 校验用户名密码并签发会话 token。
     *
     * @param username 用户名
     * @param password 密码
     * @return 会话 token；失败返回 null
     */
    public String login(String username, String password) {
        if (!securityProperties.isUiAuthEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(securityProperties.normalizedUiPassword())) {
            return null;
        }
        boolean userOk = InsightTokenSupport.equalsConstantTime(
                securityProperties.normalizedUiUsername(), username == null ? "" : username.trim());
        boolean passOk = InsightTokenSupport.equalsConstantTime(
                securityProperties.normalizedUiPassword(), password == null ? "" : password);
        if (!userOk || !passOk) {
            return null;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        int hours = Math.max(1, securityProperties.getUiSessionTtlHours());
        sessions.put(token, Instant.now().plusSeconds(hours * 3600L));
        return token;
    }

    /**
     * 注销会话。
     *
     * @param token 会话 token
     */
    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            sessions.remove(token.trim());
        }
    }

    /**
     * @param token 会话 token
     * @return 是否有效
     */
    public boolean isValid(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        Instant exp = sessions.get(token.trim());
        if (exp == null) {
            return false;
        }
        if (Instant.now().isAfter(exp)) {
            sessions.remove(token.trim());
            return false;
        }
        return true;
    }
}
