package io.github.iweidujiang.springinsight.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * insight-server 可选安全配置（默认全部关闭，兼容 0.1 接入）。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Data
@ConfigurationProperties(prefix = "spring.insight.server.security")
public class InsightServerSecurityProperties {

    /**
     * 是否强制校验上报 Token；为 true 时 {@link #ingestToken} 必须非空
     */
    private boolean ingestTokenRequired = false;

    /**
     * 上报共享密钥。非空且开启校验时，请求须带 {@code X-Insight-Token} 或 {@code Authorization: Bearer}
     */
    private String ingestToken = "";

    /**
     * 是否启用控制台 UI API 登录
     */
    private boolean uiAuthEnabled = false;

    /**
     * UI 登录用户名（仅在 {@link #uiAuthEnabled} 时生效）
     */
    private String uiUsername = "admin";

    /**
     * UI 登录密码；开启 UI 鉴权时必须非空
     */
    private String uiPassword = "";

    /**
     * UI 会话有效期（小时）
     */
    private int uiSessionTtlHours = 24;

    /**
     * @return 是否应对上报请求做 Token 校验
     */
    public boolean isIngestAuthActive() {
        return ingestTokenRequired && StringUtils.hasText(ingestToken);
    }

    /**
     * @return 规范化后的 ingest token；未配置返回空串
     */
    public String normalizedIngestToken() {
        return ingestToken == null ? "" : ingestToken.trim();
    }

    /**
     * @return 规范化 UI 用户名
     */
    public String normalizedUiUsername() {
        return uiUsername == null ? "" : uiUsername.trim();
    }

    /**
     * @return 规范化 UI 密码
     */
    public String normalizedUiPassword() {
        return uiPassword == null ? "" : uiPassword;
    }
}
