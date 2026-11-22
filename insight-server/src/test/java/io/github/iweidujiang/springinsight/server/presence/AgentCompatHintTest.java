package io.github.iweidujiang.springinsight.server.presence;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 版本配套说明单测。
 *
 * @author 苏渡苇
 * @since 2026-11-22
 */
class AgentCompatHintTest {

    @Test
    void classifyOkMismatchUnknown() {
        assertEquals(AgentCompatHint.COMPAT_OK,
                AgentCompatHint.classify("0.7.0-SNAPSHOT", "0.7.0-SNAPSHOT"));
        assertEquals(AgentCompatHint.COMPAT_MISMATCH,
                AgentCompatHint.classify("0.7.0-SNAPSHOT", "0.6.0"));
        assertEquals(AgentCompatHint.COMPAT_UNKNOWN,
                AgentCompatHint.classify("0.7.0-SNAPSHOT", null));
        assertEquals(AgentCompatHint.COMPAT_UNKNOWN,
                AgentCompatHint.classify("0.7.0-SNAPSHOT", "unknown"));
    }

    @Test
    void buildSummaryAndCompatFlags() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> ok = new HashMap<>();
        ok.put("service_name", "a");
        ok.put("agent_version", "0.7.0-SNAPSHOT");
        rows.add(ok);
        Map<String, Object> bad = new HashMap<>();
        bad.put("service_name", "b");
        bad.put("agent_version", "0.6.0");
        rows.add(bad);

        Map<String, Object> compat = AgentCompatHint.build("2.2.0-SNAPSHOT", "0.7.0-SNAPSHOT", rows);
        assertEquals(1, compat.get("mismatchCount"));
        assertEquals(2, compat.get("reportedCount"));
        assertTrue(String.valueOf(compat.get("summary")).contains("推荐配套 Agent"));
        assertEquals(AgentCompatHint.COMPAT_OK, ok.get("compat"));
        assertEquals(AgentCompatHint.COMPAT_MISMATCH, bad.get("compat"));
    }
}
