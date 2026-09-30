package io.github.iweidujiang.springinsight.agent.boot2.context;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RemoteServiceResolver} Boot2 单测。
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public class RemoteServiceResolverTest {

    /**
     * 逻辑名优先于 IP URI。
     */
    @Test
    public void resolve_prefersExplicitLogicalOverIpUri() {
        assertEquals("sca-order", RemoteServiceResolver.resolve(
                URI.create("http://127.0.0.1:8080/api"), null, "sca-order"));
    }

    /**
     * Host 头优先于已解析 IP。
     */
    @Test
    public void resolve_prefersHostHeaderOverIpUri() {
        assertEquals("sca-order", RemoteServiceResolver.resolve(
                URI.create("http://127.0.0.1:8080/x"), "sca-order:8080", null));
    }

    /**
     * 直连 IP 且无更好名字时保留 IP。
     */
    @Test
    public void resolve_keepsIpWhenNoBetterName() {
        assertEquals("127.0.0.1", RemoteServiceResolver.resolve(
                URI.create("http://127.0.0.1:8080/x"), null, null));
    }

    /**
     * 空入参 → unknown。
     */
    @Test
    public void resolve_unknownWhenMissing() {
        assertEquals("unknown", RemoteServiceResolver.resolve((URI) null, null, null));
    }

    /**
     * IP / localhost 判定。
     */
    @Test
    public void isIpOrLocalhost_detects() {
        assertTrue(RemoteServiceResolver.isIpOrLocalhost("127.0.0.1"));
        assertTrue(RemoteServiceResolver.isIpOrLocalhost("localhost"));
        assertFalse(RemoteServiceResolver.isIpOrLocalhost("sca-order"));
    }
}
