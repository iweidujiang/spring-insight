package io.github.iweidujiang.springinsight.server.presence;

import io.github.iweidujiang.springinsight.collector.model.AgentHeartbeatRequest;
import io.github.iweidujiang.springinsight.collector.model.CollectorRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 已上报 Agent 版本缓存单测。
 *
 * @author 苏渡苇
 * @since 2026-11-22
 */
class ReportedAgentMetaStoreTest {

    @Test
    void recordFromHeartbeatAndList() {
        ReportedAgentMetaStore store = new ReportedAgentMetaStore();
        AgentHeartbeatRequest req = new AgentHeartbeatRequest();
        req.setServiceName("sca-order");
        req.setServiceInstance("localhost:18081");
        req.setAgentVersion("0.7.0-SNAPSHOT");
        req.setProtocol("1");
        ReportedAgentMetaSupport.record(store, req);

        List<Map<String, Object>> rows = store.listRows();
        assertEquals(1, rows.size());
        assertEquals("sca-order", rows.get(0).get("service_name"));
        assertEquals("0.7.0-SNAPSHOT", rows.get(0).get("agent_version"));
        assertEquals("1", rows.get(0).get("protocol"));
    }

    @Test
    void recordFromCollectorMetadataFallback() {
        ReportedAgentMetaStore store = new ReportedAgentMetaStore();
        CollectorRequest req = new CollectorRequest();
        req.setServiceName("demo");
        req.setServiceInstance("i1");
        CollectorRequest.Metadata md = new CollectorRequest.Metadata();
        md.setSdkVersion("0.6.0");
        md.setProtocolVersion("1");
        req.setMetadata(md);
        ReportedAgentMetaSupport.record(store, req);

        ReportedAgentMetaStore.Entry entry = store.get("demo");
        assertNotNull(entry);
        assertEquals("0.6.0", entry.agentVersion());
        assertEquals("1", entry.protocol());
    }

    @Test
    void ignoreWhenNoVersionFields() {
        ReportedAgentMetaStore store = new ReportedAgentMetaStore();
        store.record("x", "i", null, null);
        assertNull(store.get("x"));
    }
}
