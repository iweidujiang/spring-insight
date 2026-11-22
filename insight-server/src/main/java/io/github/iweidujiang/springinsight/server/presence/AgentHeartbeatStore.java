package io.github.iweidujiang.springinsight.server.presence;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内缓存各服务最近一次 Agent 心跳时间（毫秒 epoch）。
 * <p>
 * 由 {@link ServicePresenceService} 与最近 Span 合并后判定在线 / 沉默。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026-11-20
 */
@Component
public class AgentHeartbeatStore {

    private final ConcurrentHashMap<String, Long> lastHeartbeatEpochMsByService = new ConcurrentHashMap<>();

    /**
     * 记录一次心跳。
     *
     * @param serviceName       服务名；空则忽略
     * @param heartbeatEpochMs  心跳时刻；{@code <=0} 时用当前时间
     */
    public void record(String serviceName, long heartbeatEpochMs) {
        String name = serviceName == null ? "" : serviceName.trim();
        if (name.isEmpty()) {
            return;
        }
        long ts = heartbeatEpochMs > 0L ? heartbeatEpochMs : System.currentTimeMillis();
        lastHeartbeatEpochMsByService.merge(name, ts, Math::max);
    }

    /**
     * 查询某服务最近心跳。
     *
     * @param serviceName 服务名
     * @return 最近心跳 epoch ms；无记录为空
     */
    public OptionalLong getLastHeartbeatEpochMs(String serviceName) {
        String name = serviceName == null ? "" : serviceName.trim();
        if (name.isEmpty()) {
            return OptionalLong.empty();
        }
        Long v = lastHeartbeatEpochMsByService.get(name);
        return v == null ? OptionalLong.empty() : OptionalLong.of(v);
    }

    /**
     * @return 不可变快照：服务名 → 最近心跳 epoch ms
     */
    public Map<String, Long> snapshot() {
        return Map.copyOf(lastHeartbeatEpochMsByService);
    }
}
