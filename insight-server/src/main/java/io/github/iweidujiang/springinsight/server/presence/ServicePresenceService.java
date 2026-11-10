package io.github.iweidujiang.springinsight.server.presence;

import io.github.iweidujiang.springinsight.server.config.InsightServerPresenceProperties;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按最近 Span 时间判定服务 Online / Stale（薄档，无 Agent 心跳）。
 *
 * @author 苏渡苇
 * @since 2026-11-10
 */
@Service
public class ServicePresenceService {

    /** 近期有上报 */
    public static final String ONLINE = "online";
    /** 超过阈值无新 Span */
    public static final String STALE = "stale";
    /** 未见过任何 Span */
    public static final String UNKNOWN = "unknown";

    private final TraceSpanPersistenceService persistenceService;
    private final InsightServerPresenceProperties properties;

    /**
     * @param persistenceService Span 查询门面
     * @param properties         沉默阈值
     */
    public ServicePresenceService(TraceSpanPersistenceService persistenceService,
                                  InsightServerPresenceProperties properties) {
        this.persistenceService = persistenceService;
        this.properties = properties;
    }

    /**
     * 根据最近活动时间判定存活状态。
     *
     * @param lastActivityEpochMs 最近 Span 开始时间；{@code null}/{@code <=0} 视为未知
     * @param nowEpochMs          当前时刻
     * @return {@link #ONLINE} / {@link #STALE} / {@link #UNKNOWN}
     */
    public String classify(Long lastActivityEpochMs, long nowEpochMs) {
        if (lastActivityEpochMs == null || lastActivityEpochMs <= 0L) {
            return UNKNOWN;
        }
        long age = Math.max(0L, nowEpochMs - lastActivityEpochMs);
        if (age <= properties.staleThresholdMillis()) {
            return ONLINE;
        }
        return STALE;
    }

    /**
     * 全量服务存活列表（按服务名排序）。
     *
     * @return 每行含 presence / last_activity_* / quiet_minutes 等
     */
    public List<Map<String, Object>> listPresence() {
        long now = System.currentTimeMillis();
        Map<String, Long> activity = persistenceService.getLastActivityEpochMsByService();
        List<String> names = new ArrayList<>(activity.keySet());
        names.sort(String::compareTo);
        List<Map<String, Object>> out = new ArrayList<>(names.size());
        for (String name : names) {
            out.add(toRow(name, activity.get(name), now));
        }
        return out;
    }

    /**
     * 单个服务的存活快照。
     *
     * @param serviceName 服务名
     * @return 存活字段；服务名为空时 presence=unknown
     */
    public Map<String, Object> presenceFor(String serviceName) {
        long now = System.currentTimeMillis();
        String svc = serviceName == null ? "" : serviceName.trim();
        if (svc.isEmpty()) {
            return toRow("", null, now);
        }
        Long last = persistenceService.getLastActivityEpochMsByService().get(svc);
        return toRow(svc, last, now);
    }

    /**
     * 把存活字段挂到已有服务行（按 {@code service_name} / {@code serviceName} 匹配）。
     *
     * @param rows 延迟摘要等行
     * @return 同一列表（原地补充字段）
     */
    public List<Map<String, Object>> attachPresence(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        long now = System.currentTimeMillis();
        Map<String, Long> activity = persistenceService.getLastActivityEpochMsByService();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object rawName = row.containsKey("service_name") ? row.get("service_name") : row.get("serviceName");
            String name = rawName == null ? "" : String.valueOf(rawName).trim();
            Map<String, Object> presence = toRow(name, activity.get(name), now);
            row.putAll(presence);
            // 保留原 service_name，避免被空 name 覆盖
            if (!name.isEmpty() && !row.containsKey("service_name")) {
                row.put("service_name", name);
            }
        }
        return rows;
    }

    /**
     * 组装一行存活字段（不含重复写 service_name 以外的业务 KPI）。
     *
     * @param serviceName         服务名
     * @param lastActivityEpochMs 最近活动
     * @param nowEpochMs          当前时刻
     * @return 字段 Map
     */
    Map<String, Object> toRow(String serviceName, Long lastActivityEpochMs, long nowEpochMs) {
        String presence = classify(lastActivityEpochMs, nowEpochMs);
        int threshold = properties.normalizedStaleThresholdMinutes();
        Map<String, Object> row = new LinkedHashMap<>();
        if (serviceName != null && !serviceName.isEmpty()) {
            row.put("service_name", serviceName);
        }
        row.put("presence", presence);
        row.put("stale_threshold_minutes", threshold);
        if (lastActivityEpochMs != null && lastActivityEpochMs > 0L) {
            row.put("last_activity_epoch_ms", lastActivityEpochMs);
            row.put("last_activity_at", Instant.ofEpochMilli(lastActivityEpochMs)
                    .truncatedTo(ChronoUnit.SECONDS)
                    .toString());
            long quietMs = Math.max(0L, nowEpochMs - lastActivityEpochMs);
            row.put("quiet_minutes", quietMs / 60_000L);
        } else {
            row.put("last_activity_epoch_ms", null);
            row.put("last_activity_at", null);
            row.put("quiet_minutes", null);
        }
        return row;
    }
}
