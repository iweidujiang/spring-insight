package io.github.iweidujiang.springinsight.server.alert;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 进程内告警历史环形缓冲（触发 / 恢复 / 升级）；重启清空。
 *
 * @author 苏渡苇
 * @since 2026-11-10
 */
@Component
public class AlertHistoryStore {

    /** 最多保留条数 */
    public static final int MAX_EVENTS = 200;

    public static final String EVENT_FIRE = "fire";
    public static final String EVENT_RECOVER = "recover";
    public static final String EVENT_ESCALATE = "escalate";

    private final ArrayDeque<Map<String, Object>> events = new ArrayDeque<>();

    /**
     * 记录一条告警历史。
     *
     * @param event           {@link #EVENT_FIRE} / {@link #EVENT_RECOVER} / {@link #EVENT_ESCALATE}
     * @param serviceName     服务名
     * @param metric          指标
     * @param value           当前值
     * @param threshold       阈值
     * @param atIso           ISO 时间
     * @param atEpochMs       epoch 毫秒
     * @param durationMinutes 持续分钟（恢复 / 升级）；无则 null
     * @param delivered       是否成功推送到渠道
     * @param matchedConditions 命中条件（可空）
     */
    public void record(String event,
                       String serviceName,
                       String metric,
                       Double value,
                       Double threshold,
                       String atIso,
                       long atEpochMs,
                       Long durationMinutes,
                       boolean delivered,
                       List<Map<String, Object>> matchedConditions) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID().toString());
        row.put("event", event == null ? "" : event);
        row.put("serviceName", serviceName == null ? "" : serviceName);
        row.put("metric", metric == null ? "" : metric);
        if (value != null) {
            row.put("value", value);
        }
        if (threshold != null) {
            row.put("threshold", threshold);
        }
        row.put("at", atIso);
        row.put("atEpochMs", atEpochMs);
        if (durationMinutes != null) {
            row.put("durationMinutes", durationMinutes);
        }
        row.put("delivered", delivered);
        if (matchedConditions != null && !matchedConditions.isEmpty()) {
            row.put("matchedConditions", matchedConditions);
        }
        synchronized (events) {
            events.addFirst(row);
            while (events.size() > MAX_EVENTS) {
                events.removeLast();
            }
        }
    }

    /**
     * 最近历史（新在前）。
     *
     * @param limit 条数
     * @return 副本列表
     */
    public List<Map<String, Object>> recent(int limit) {
        int max = Math.max(1, Math.min(limit, MAX_EVENTS));
        synchronized (events) {
            List<Map<String, Object>> out = new ArrayList<>(Math.min(max, events.size()));
            int i = 0;
            for (Map<String, Object> row : events) {
                if (i++ >= max) {
                    break;
                }
                out.add(new LinkedHashMap<>(row));
            }
            return out;
        }
    }

    /**
     * @return 当前缓冲条数（测试用）
     */
    int size() {
        synchronized (events) {
            return events.size();
        }
    }
}
