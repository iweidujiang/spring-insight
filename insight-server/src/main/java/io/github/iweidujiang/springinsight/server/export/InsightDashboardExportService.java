package io.github.iweidujiang.springinsight.server.export;

import io.github.iweidujiang.springinsight.collector.service.TraceSpanCollectorService;
import io.github.iweidujiang.springinsight.server.insight.InsightPeriodInsightService;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组装仪表盘快照 JSON（KPI、时段摘要、Top 列表），供导出下载。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
@Service
@RequiredArgsConstructor
public class InsightDashboardExportService {

    private static final int TOP_LATENCY = 20;
    private static final int TOP_RECENT = 16;

    private final TraceSpanPersistenceService persistenceService;
    private final InsightPeriodInsightService periodInsightService;
    private final TraceSpanCollectorService collectorService;

    /**
     * 构建与控制台仪表盘时间窗一致的快照。
     *
     * @param hours 窗口小时；{@code <=0} 表示全部已存（近期链路按 168 小时）
     * @return 可序列化的快照 Map
     */
    public Map<String, Object> buildSnapshot(int hours) {
        Map<String, Object> period = periodInsightService.build(hours);
        @SuppressWarnings("unchecked")
        Map<String, Object> current = period.get("current") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

        List<Map<String, Object>> slowServices =
                persistenceService.getServiceLatencySummaries(hours, TOP_LATENCY);
        List<Map<String, Object>> errorServices =
                persistenceService.findHighErrorServices(hours);
        int recentHours = hours <= 0 ? 168 : hours;
        List<Map<String, Object>> recentTraces =
                persistenceService.getRecentTraceSummaries(
                        recentHours, TOP_RECENT, null, "ALL", null, 0L, null);

        Map<String, Object> jdbcSummary = safeJdbc(hours);
        Map<String, Object> redisSummary = safeRedis(hours);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("spanCount", current.getOrDefault("spanCount", 0));
        kpis.put("errorSpanCount", current.getOrDefault("errorSpanCount", 0));
        kpis.put("errorServiceCount", listSize(current.get("errorServices")));
        kpis.put("slowServiceCount", slowServices.size());
        kpis.put("serviceCount", persistenceService.getSpanCountByService(hours).size());
        if (jdbcSummary != null && jdbcSummary.get("kpis") != null) {
            kpis.put("jdbc", jdbcSummary.get("kpis"));
        }
        if (redisSummary != null && redisSummary.get("kpis") != null) {
            kpis.put("redis", redisSummary.get("kpis"));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("headline", period.get("headline"));
        summary.put("window", period.get("window"));
        summary.put("compare", period.get("compare"));
        summary.put("delta", period.get("delta"));
        summary.put("current", current);

        Map<String, Object> tops = new LinkedHashMap<>();
        tops.put("slowServices", slowServices);
        tops.put("errorServices", errorServices);
        tops.put("recentTraces", recentTraces);
        if (current.get("hotEdges") != null) {
            tops.put("hotEdges", current.get("hotEdges"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", 1);
        body.put("hours", hours);
        body.put("exportedAt", Instant.now().toString());
        body.put("kpis", kpis);
        body.put("summary", summary);
        body.put("period", period);
        body.put("tops", tops);
        body.put("dependencies", persistenceService.getServiceDependencies(hours));
        body.put("serviceStats", persistenceService.getSpanCountByService(hours));
        body.put("collectorStats", collectorService.getStats());
        return body;
    }

    private Map<String, Object> safeJdbc(int hours) {
        try {
            return persistenceService.getJdbcSummary(hours, null, 1);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> safeRedis(int hours) {
        try {
            return persistenceService.getRedisSummary(hours, null, 1);
        } catch (Exception e) {
            return null;
        }
    }

    private static int listSize(Object v) {
        if (v instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }
}
