package io.github.iweidujiang.springinsight.server.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Token 比较与鉴权开关单元测试。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class InsightServerSecurityTest {

    /**
     * 常量时间比较：相同通过、不同失败。
     */
    @Test
    void equalsConstantTime() {
        assertTrue(InsightTokenSupport.equalsConstantTime("abc", "abc"));
        assertFalse(InsightTokenSupport.equalsConstantTime("abc", "abd"));
        assertFalse(InsightTokenSupport.equalsConstantTime("abc", null));
    }

    /**
     * ingest 鉴权仅在 required=true 且 token 非空时激活。
     */
    @Test
    void ingestAuthActiveOnlyWhenConfigured() {
        io.github.iweidujiang.springinsight.server.config.InsightServerSecurityProperties p =
                new io.github.iweidujiang.springinsight.server.config.InsightServerSecurityProperties();
        assertFalse(p.isIngestAuthActive());
        p.setIngestTokenRequired(true);
        assertFalse(p.isIngestAuthActive());
        p.setIngestToken("secret");
        assertTrue(p.isIngestAuthActive());
    }

    /**
     * UI 登录成功签发 token，错误密码拒绝。
     */
    @Test
    void uiSessionLoginAndValidate() {
        io.github.iweidujiang.springinsight.server.config.InsightServerSecurityProperties p =
                new io.github.iweidujiang.springinsight.server.config.InsightServerSecurityProperties();
        p.setUiAuthEnabled(true);
        p.setUiUsername("admin");
        p.setUiPassword("pass");
        UiSessionStore store = new UiSessionStore(p);
        assertTrue(store.login("admin", "bad") == null);
        String token = store.login("admin", "pass");
        assertTrue(token != null && store.isValid(token));
        store.logout(token);
        assertFalse(store.isValid(token));
    }
}
