package io.github.iweidujiang.springinsight.server.presence;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存各服务最近一次上报中的 Agent 版本与协议号。
 *
 * @author 苏渡苇
 * @since 2026-11-22
 */
@Component
public class ReportedAgentMetaStore {

    private final ConcurrentHashMap<String, Entry> byService = new ConcurrentHashMap<>();

    /**
     * 记录一次上报中的版本元数据；版本与协议皆空时忽略。
     *
     * @param serviceName     服务名
     * @param serviceInstance 实例（可空）
     * @param agentVersion    Agent 版本（可空）
     * @param protocol        协议号（可空）
     */
    public void record(String serviceName, String serviceInstance, String agentVersion, String protocol) {
        String name = serviceName == null ? "" : serviceName.trim();
        if (name.isEmpty()) {
            return;
        }
        String version = trimToNull(agentVersion);
        String proto = trimToNull(protocol);
        if (version == null && proto == null) {
            return;
        }
        long now = System.currentTimeMillis();
        byService.merge(name, new Entry(name, trimToEmpty(serviceInstance), version, proto, now),
                (old, neu) -> new Entry(
                        name,
                        StringUtils.hasText(neu.serviceInstance) ? neu.serviceInstance : old.serviceInstance,
                        neu.agentVersion != null ? neu.agentVersion : old.agentVersion,
                        neu.protocol != null ? neu.protocol : old.protocol,
                        now));
    }

    /**
     * @param serviceName 服务名
     * @return 最近上报元数据；无则 {@code null}
     */
    public Entry get(String serviceName) {
        String name = serviceName == null ? "" : serviceName.trim();
        if (name.isEmpty()) {
            return null;
        }
        return byService.get(name);
    }

    /**
     * @return 按服务名排序的快照行（供 API / 关于页）
     */
    public List<Map<String, Object>> listRows() {
        List<String> names = new ArrayList<>(byService.keySet());
        names.sort(String::compareTo);
        List<Map<String, Object>> out = new ArrayList<>(names.size());
        for (String name : names) {
            Entry e = byService.get(name);
            if (e != null) {
                out.add(e.toRow());
            }
        }
        return out;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * 单服务最近上报元数据。
     *
     * @param serviceName     服务名
     * @param serviceInstance 实例
     * @param agentVersion    Agent 版本
     * @param protocol        协议号
     * @param lastSeenEpochMs 最近见到时刻
     */
    public record Entry(String serviceName,
                        String serviceInstance,
                        String agentVersion,
                        String protocol,
                        long lastSeenEpochMs) {

        /**
         * @return API 行字段
         */
        Map<String, Object> toRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("service_name", serviceName);
            row.put("service_instance", serviceInstance);
            row.put("agent_version", agentVersion);
            row.put("protocol", protocol);
            row.put("last_seen_epoch_ms", lastSeenEpochMs);
            row.put("last_seen_at", Instant.ofEpochMilli(lastSeenEpochMs)
                    .truncatedTo(ChronoUnit.SECONDS)
                    .toString());
            return row;
        }
    }
}
