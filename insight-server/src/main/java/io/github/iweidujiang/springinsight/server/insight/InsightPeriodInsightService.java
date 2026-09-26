package io.github.iweidujiang.springinsight.server.insight;

import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 近 N 小时时段事实与上一窗环比（无 AI）。
 *
 * @since 2026-09-26
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Service
@RequiredArgsConstructor
public class InsightPeriodInsightService {

    private static final int TOP_N = 5;

    private final TraceSpanPersistenceService persistenceService;

    /**
     * 构建时段洞察事实体。
     *
     * @param hours 窗口小时；{@code <=0} 表示全部已存 Span，不做环比
     * @return 含 window / current / delta / links 的结构
     */
    public Map<String, Object> build(int hours) {
        long now = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", 1);
        body.put("hours", hours);

        if (hours <= 0) {
            Map<String, Object> current = persistenceService.summarizeWindow(0L, 0L, TOP_N);
            body.put("compare", false);
            body.put("window", windowMap(0L, now));
            body.put("previousWindow", null);
            body.put("current", current);
            body.put("previous", null);
            body.put("delta", null);
            body.put("links", buildLinks(hours, current));
            body.put("headline", headlineNoCompare(current));
            return body;
        }

        long windowMs = hours * 3_600_000L;
        long currentFrom = now - windowMs;
        long previousFrom = currentFrom - windowMs;

        Map<String, Object> current = persistenceService.summarizeWindow(currentFrom, now, TOP_N);
        Map<String, Object> previous = persistenceService.summarizeWindow(previousFrom, currentFrom, TOP_N);

        body.put("compare", true);
        body.put("window", windowMap(currentFrom, now));
        body.put("previousWindow", windowMap(previousFrom, currentFrom));
        body.put("current", current);
        body.put("previous", previous);
        body.put("delta", buildDelta(current, previous));
        body.put("links", buildLinks(hours, current));
        body.put("headline", headlineWithCompare(current, body.get("delta")));
        return body;
    }

    /**
     * @param from 起始 epoch ms
     * @param to   结束 epoch ms
     * @return 窗口描述
     */
    private static Map<String, Object> windowMap(long from, long to) {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("fromEpochMs", from);
        w.put("toEpochMs", to);
        w.put("from", from > 0 ? Instant.ofEpochMilli(from).truncatedTo(ChronoUnit.SECONDS).toString() : "");
        w.put("to", Instant.ofEpochMilli(to).truncatedTo(ChronoUnit.SECONDS).toString());
        return w;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildDelta(Map<String, Object> current, Map<String, Object> previous) {
        long curErr = num(current.get("errorSpanCount"));
        long prevErr = num(previous.get("errorSpanCount"));
        long curSpans = num(current.get("spanCount"));
        long prevSpans = num(previous.get("spanCount"));

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("errorSpanCount", curErr - prevErr);
        delta.put("spanCount", curSpans - prevSpans);
        delta.put("errorServiceCount",
                listSize(current.get("errorServices")) - listSize(previous.get("errorServices")));

        List<String> newErrorServices = new ArrayList<>();
        List<Map<String, Object>> curSvc = (List<Map<String, Object>>) current.getOrDefault("errorServices", List.of());
        List<Map<String, Object>> prevSvc = (List<Map<String, Object>>) previous.getOrDefault("errorServices", List.of());
        java.util.Set<String> prevNames = new java.util.HashSet<>();
        for (Map<String, Object> row : prevSvc) {
            prevNames.add(String.valueOf(row.get("serviceName")));
        }
        for (Map<String, Object> row : curSvc) {
            String name = String.valueOf(row.get("serviceName"));
            if (!prevNames.contains(name)) {
                newErrorServices.add(name);
            }
        }
        delta.put("newErrorServices", newErrorServices);
        return delta;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildLinks(int hours, Map<String, Object> current) {
        Map<String, Object> links = new LinkedHashMap<>();
        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("path", "/error-analysis");
        errors.put("query", hours > 0 ? Map.of("hours", hours) : Map.of());
        links.put("errorAnalysis", errors);

        Map<String, Object> errorTraces = new LinkedHashMap<>();
        errorTraces.put("path", "/traces");
        Map<String, Object> etq = new LinkedHashMap<>();
        etq.put("status", "error");
        if (hours > 0) {
            etq.put("hours", hours);
        }
        errorTraces.put("query", etq);
        links.put("errorTraces", errorTraces);

        List<String> slowIds = (List<String>) current.getOrDefault("sampleSlowTraceIds", List.of());
        if (!slowIds.isEmpty()) {
            links.put("sampleSlowTrace", Map.of(
                    "path", "/traces/" + slowIds.get(0),
                    "traceId", slowIds.get(0)));
        }
        List<String> errIds = (List<String>) current.getOrDefault("sampleErrorTraceIds", List.of());
        if (!errIds.isEmpty()) {
            links.put("sampleErrorTrace", Map.of(
                    "path", "/traces/" + errIds.get(0),
                    "traceId", errIds.get(0)));
        }

        List<Map<String, Object>> edges = (List<Map<String, Object>>) current.getOrDefault("hotEdges", List.of());
        if (!edges.isEmpty()) {
            Map<String, Object> edge = edges.get(0);
            Map<String, Object> tq = new LinkedHashMap<>();
            tq.put("source", edge.get("sourceService"));
            tq.put("target", edge.get("targetService"));
            if (hours > 0) {
                tq.put("hours", hours);
            }
            links.put("hotEdge", Map.of("path", "/topology", "query", tq));
        }
        return links;
    }

    @SuppressWarnings("unchecked")
    private static String headlineNoCompare(Map<String, Object> current) {
        long err = num(current.get("errorSpanCount"));
        long spans = num(current.get("spanCount"));
        if (spans <= 0) {
            return "暂无 Span 数据";
        }
        if (err <= 0) {
            return "已存 " + spans + " 条 Span，当前无错误 Span";
        }
        return "已存 " + spans + " 条 Span，其中错误 " + err + " 条";
    }

    @SuppressWarnings("unchecked")
    private static String headlineWithCompare(Map<String, Object> current, Object deltaObj) {
        long err = num(current.get("errorSpanCount"));
        long spans = num(current.get("spanCount"));
        if (spans <= 0) {
            return "近窗内暂无 Span";
        }
        Map<String, Object> delta = deltaObj instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        long dErr = num(delta.get("errorSpanCount"));
        String trend;
        if (dErr > 0) {
            trend = "错误 Span 较上一窗 +" + dErr;
        } else if (dErr < 0) {
            trend = "错误 Span 较上一窗 " + dErr;
        } else {
            trend = "错误 Span 与上一窗持平";
        }
        if (err <= 0) {
            return "近窗 " + spans + " 条 Span，无错误；" + trend;
        }
        return "近窗 " + spans + " 条 Span，错误 " + err + " 条；" + trend;
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static int listSize(Object o) {
        return o instanceof List<?> l ? l.size() : 0;
    }
}
