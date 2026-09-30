package io.github.iweidujiang.springinsight.agent.context;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RemoteServiceResolver} 单测。
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class RemoteServiceResolverTest {

    /**
     * 逻辑名优先于 IP URI。
     */
    @Test
    void resolve_prefersExplicitLogicalOverIpUri() {
        assertEquals("sca-order", RemoteServiceResolver.resolve(
                URI.create("http://127.0.0.1:8080/api"), null, "sca-order"));
    }

    /**
     * Host 头优先于已解析 IP。
     */
    @Test
    void resolve_prefersHostHeaderOverIpUri() {
        assertEquals("sca-order", RemoteServiceResolver.resolve(
                URI.create("http://127.0.0.1:8080/x"), "sca-order:8080", null));
    }

    /**
     * 直连 IP 且无更好名字时保留 IP。
     */
    @Test
    void resolve_keepsIpWhenNoBetterName() {
        assertEquals("127.0.0.1", RemoteServiceResolver.resolve(
                URI.create("http://127.0.0.1:8080/x"), null, null));
    }

    /**
     * 非 IP URI host 直接采用。
     */
    @Test
    void resolve_usesServiceHost() {
        assertEquals("sca-user", RemoteServiceResolver.resolve(
                URI.create("http://sca-user/api"), null, null));
    }

    /**
     * 空入参 → unknown。
     */
    @Test
    void resolve_unknownWhenMissing() {
        assertEquals("unknown", RemoteServiceResolver.resolve((URI) null, null, null));
    }

    /**
     * IP / localhost 判定。
     */
    @Test
    void isIpOrLocalhost_detects() {
        assertTrue(RemoteServiceResolver.isIpOrLocalhost("127.0.0.1"));
        assertTrue(RemoteServiceResolver.isIpOrLocalhost("localhost"));
        assertTrue(RemoteServiceResolver.isIpOrLocalhost("[::1]"));
        assertFalse(RemoteServiceResolver.isIpOrLocalhost("sca-order"));
        assertFalse(RemoteServiceResolver.isIpOrLocalhost("order.svc"));
    }

    /**
     * Host 去端口。
     */
    @Test
    void hostWithoutPort_strips() {
        assertEquals("sca-order", RemoteServiceResolver.hostWithoutPort("sca-order:8080"));
        assertEquals("[::1]", RemoteServiceResolver.hostWithoutPort("[::1]:8080"));
    }
}
