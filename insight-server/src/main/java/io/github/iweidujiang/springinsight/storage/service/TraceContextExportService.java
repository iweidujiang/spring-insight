package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将单条 Trace 导出为稳定、脱敏的 Context JSON（AI 地基，不调模型）。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Service
@RequiredArgsConstructor
public class TraceContextExportService {

    /** 与 ai-roadmap Context 草案对齐 */
    public static final String SCHEMA_VERSION = "1";

    private static final int MAX_ERROR_MESSAGE_CHARS = 300;
    private static final Pattern HTTP_CODE = Pattern.compile("HTTP[_\\s-]?(\\d{3})", Pattern.CASE_INSENSITIVE);

    private final SpanStore spanStore;

    /**
     * 按 Trace ID 构建 Context；无 Span 时返回 {@code null}。
     *
     * @param traceId Trace ID
     * @return context Map，或 null（未找到）
     */
    public Map<String, Object> buildContext(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        List<TraceSpan> spans = spanStore.findByTraceId(traceId.trim());
        if (spans == null || spans.isEmpty()) {
            return null;
        }
        return buildFromSpans(traceId.trim(), spans);
    }

    /**
     * 从已加载的 Span 列表构建 Context（便于单测）。
     *
     * @param traceId Trace ID
     * @param spans   同 Trace 下的 Span；调用方保证非空
     * @return context JSON 结构
     */
    public Map<String, Object> buildFromSpans(String traceId, List<TraceSpan> spans) {
        List<TraceSpan> ordered = new ArrayList<>(spans);
        ordered.sort(Comparator
                .comparingLong((TraceSpan s) -> n(s.getStartTime()))
                .thenComparing(s -> s.getSpanId() != null ? s.getSpanId() : "", String::compareTo));

        long minStart = Long.MAX_VALUE;
        long maxEnd = 0L;
        int errorCount = 0;
        String rootService = null;
        long rootStart = Long.MAX_VALUE;
        String slowestSpanId = null;
        long slowestDuration = -1L;
        String firstErrorSpanId = null;
        long firstErrorStart = Long.MAX_VALUE;

        List<Map<String, Object>> spanRows = new ArrayList<>(ordered.size());
        LinkedHashSet<String> path = new LinkedHashSet<>();

        for (TraceSpan s : ordered) {
            if (s == null) {
                continue;
            }
            long start = n(s.getStartTime());
            long end = n(s.getEndTime());
            long dur = n(s.getDurationMs());
            if (dur <= 0 && end > start) {
                dur = end - start;
            }
            if (end <= 0) {
                end = start + dur;
            }
            if (start > 0) {
                minStart = Math.min(minStart, start);
            }
            if (end > 0) {
                maxEnd = Math.max(maxEnd, end);
            }

            boolean error = isError(s);
            if (error) {
                errorCount++;
                if (start > 0 && start < firstErrorStart && s.getSpanId() != null) {
                    firstErrorStart = start;
                    firstErrorSpanId = s.getSpanId();
                }
            }
            if (dur >= slowestDuration && s.getSpanId() != null) {
                slowestDuration = dur;
                slowestSpanId = s.getSpanId();
            }

            boolean root = s.getParentSpanId() == null || s.getParentSpanId().isBlank();
            if (root && start < rootStart && s.getServiceName() != null && !s.getServiceName().isBlank()) {
                rootStart = start;
                rootService = s.getServiceName();
            }

            if (s.getServiceName() != null && !s.getServiceName().isBlank()) {
                path.add(s.getServiceName());
            }
            // remoteService：补全跨服务路径上尚未出现的下游
            if (s.getRemoteService() != null && !s.getRemoteService().isBlank()) {
                path.add(s.getRemoteService());
            }

            spanRows.add(toSpanRow(s, dur, error));
        }

        if (rootService == null) {
            for (TraceSpan s : ordered) {
                if (s != null && s.getServiceName() != null && !s.getServiceName().isBlank()) {
                    rootService = s.getServiceName();
                    break;
                }
            }
        }

        long durationMs = 0L;
        if (minStart != Long.MAX_VALUE && maxEnd >= minStart) {
            durationMs = maxEnd - minStart;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rootService", rootService != null ? rootService : "");
        summary.put("durationMs", durationMs);
        summary.put("success", errorCount == 0);
        summary.put("spanCount", spanRows.size());
        summary.put("errorCount", errorCount);

        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("slowestSpanId", slowestSpanId != null ? slowestSpanId : "");
        hints.put("firstErrorSpanId", firstErrorSpanId != null ? firstErrorSpanId : "");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", SCHEMA_VERSION);
        body.put("traceId", traceId);
        body.put("summary", summary);
        body.put("path", new ArrayList<>(path));
        body.put("spans", spanRows);
        body.put("hints", hints);
        return body;
    }

    /**
     * 单条 Span 的脱敏摘要行。
     *
     * @param s     原始 Span
     * @param dur   已算好的耗时
     * @param error 是否错误
     * @return 行 Map
     */
    private static Map<String, Object> toSpanRow(TraceSpan s, long dur, boolean error) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("spanId", s.getSpanId() != null ? s.getSpanId() : "");
        row.put("service", s.getServiceName() != null ? s.getServiceName() : "");
        row.put("name", s.getOperationName() != null ? s.getOperationName() : "");
        row.put("kind", s.getSpanKind() != null ? s.getSpanKind() : "");
        row.put("durationMs", Math.max(0L, dur));
        row.put("success", !error);
        row.put("statusCode", resolveStatusCode(s, error));
        row.put("errorMessage", sanitizeMessage(error ? s.getErrorMessage() : null));
        return row;
    }

    /**
     * 优先 HTTP 数字状态码，否则 OK/ERROR。
     *
     * @param s     Span
     * @param error 是否错误
     * @return 状态码字符串
     */
    private static String resolveStatusCode(TraceSpan s, boolean error) {
        if (s.getTags() != null) {
            String http = s.getTags().get("http.status_code");
            if (http != null && !http.isBlank()) {
                return http.trim();
            }
        }
        String code = s.getErrorCode();
        if (code != null) {
            Matcher m = HTTP_CODE.matcher(code);
            if (m.find()) {
                return m.group(1);
            }
        }
        if (s.getStatusCode() != null && !s.getStatusCode().isBlank()) {
            return s.getStatusCode();
        }
        return error ? "ERROR" : "OK";
    }

    /**
     * 截断错误信息，避免把超长堆栈/敏感细节整段导出。
     *
     * @param message 原始错误信息
     * @return 脱敏后文本；无错误时为空串
     */
    private static String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String t = message.trim().replace('\r', ' ').replace('\n', ' ');
        if (t.length() <= MAX_ERROR_MESSAGE_CHARS) {
            return t;
        }
        return t.substring(0, MAX_ERROR_MESSAGE_CHARS) + "...";
    }

    /**
     * @param s Span
     * @return 是否视为错误
     */
    private static boolean isError(TraceSpan s) {
        if ("ERROR".equalsIgnoreCase(s.getStatusCode())) {
            return true;
        }
        return Boolean.FALSE.equals(s.getSuccess());
    }

    /**
     * @param v 可空 Long
     * @return 非空值或 0
     */
    private static long n(Long v) {
        return v != null ? v : 0L;
    }
}
