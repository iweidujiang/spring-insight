package io.github.iweidujiang.springinsight.server.presence;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存各服务最近一次心跳中的队列丢弃 / 深度。
 *
 * @author 苏渡苇
 * @since 2026-11-22
 */
@Component
public class AgentDropStatsStore {

    private final ConcurrentHashMap<String, Entry> byService = new ConcurrentHashMap<>();

    /**
     * 记录一次心跳中的丢弃统计；字段皆空时忽略。
     *
     * @param serviceName   服务名
     * @param queueDropped 累计丢弃
     * @param queueSize    当前深度
     * @param queueCapacity 容量
     */
    public void record(String serviceName, Long queueDropped, Integer queueSize, Integer queueCapacity) {
        String name = serviceName == null ? "" : serviceName.trim();
        if (name.isEmpty()) {
            return;
        }
        if (queueDropped == null && queueSize == null && queueCapacity == null) {
            return;
        }
        byService.put(name, new Entry(
                queueDropped != null ? queueDropped : 0L,
                queueSize != null ? queueSize : 0,
                queueCapacity != null ? queueCapacity : 0,
                System.currentTimeMillis()));
    }

    /**
     * @param serviceName 服务名
     * @return 最近统计；无则 {@code null}
     */
    public Entry get(String serviceName) {
        String name = serviceName == null ? "" : serviceName.trim();
        if (name.isEmpty()) {
            return null;
        }
        return byService.get(name);
    }

    /**
     * @param serviceName 服务名
     * @return API 字段 Map；无记录返回空 Map
     */
    public Map<String, Object> toMap(String serviceName) {
        Entry e = get(serviceName);
        if (e == null) {
            return Map.of();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("queue_dropped", e.queueDropped());
        row.put("queue_size", e.queueSize());
        row.put("queue_capacity", e.queueCapacity());
        row.put("updated_epoch_ms", e.updatedEpochMs());
        return row;
    }

    /**
     * @param queueDropped  累计丢弃
     * @param queueSize     当前深度
     * @param queueCapacity 容量
     * @param updatedEpochMs 更新时刻
     */
    public record Entry(long queueDropped, int queueSize, int queueCapacity, long updatedEpochMs) {
    }
}
