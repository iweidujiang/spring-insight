package io.github.iweidujiang.springinsight.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 构建元信息单测。
 *
 * @author 苏渡苇
 * @since 2026-11-22
 */
class AgentBuildInfoTest {

    @Test
    void protocolIsStable() {
        assertEquals("1", AgentBuildInfo.protocol());
    }

    @Test
    void agentVersionNotBlank() {
        String v = AgentBuildInfo.agentVersion();
        assertFalse(v == null || v.isBlank());
        assertTrue(v.equals("unknown") || v.contains(".") || v.contains("SNAPSHOT"),
                "unexpected version: " + v);
    }
}
