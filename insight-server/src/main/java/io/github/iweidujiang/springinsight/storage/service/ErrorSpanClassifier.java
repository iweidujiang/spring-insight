package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将错误 Span 归类为 HTTP 状态码 / 异常类 / 其它，供错误分析聚合使用。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class ErrorSpanClassifier {

    private static final Pattern HTTP_CODE = Pattern.compile("HTTP[_\\s-]?(\\d{3})", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCEPTIONISH = Pattern.compile(
            "^([\\w.$]+(?:Exception|Error|Throwable))(?:\\s*:|$)");

    private ErrorSpanClassifier() {
    }

    /**
     * 错误分类结果。
     *
     * @param kind  status / exception / other
     * @param key   聚合键（如 404、NullPointerException）
     * @param label 展示文案
     */
    public record Classification(String kind, String key, String label) {
    }

    /**
     * @param span 错误 Span；null 时归为 other/UNKNOWN
     * @return 分类
     */
    public static Classification classify(TraceSpan span) {
        if (span == null) {
            return new Classification("other", "UNKNOWN", "未知错误");
        }

        String fromTag = httpStatusFromTags(span.getTags());
        if (fromTag != null) {
            return status(fromTag);
        }

        String errorCode = blankToNull(span.getErrorCode());
        if (errorCode != null) {
            Matcher m = HTTP_CODE.matcher(errorCode);
            if (m.find()) {
                return status(m.group(1));
            }
        }

        String message = blankToNull(span.getErrorMessage());
        if (message != null) {
            Matcher httpInMsg = HTTP_CODE.matcher(message);
            if (httpInMsg.find()) {
                return status(httpInMsg.group(1));
            }
            String ex = exceptionSimpleName(message);
            if (ex != null) {
                return new Classification("exception", ex, ex);
            }
        }

        if (errorCode != null && !"EXCEPTION".equalsIgnoreCase(errorCode) && !HTTP_CODE.matcher(errorCode).find()) {
            return new Classification("other", errorCode, errorCode);
        }
        if ("EXCEPTION".equalsIgnoreCase(errorCode)) {
            return new Classification("exception", "EXCEPTION", "EXCEPTION");
        }
        return new Classification("other", "UNKNOWN", "未知错误");
    }

    /**
     * @param code HTTP 状态码数字串
     * @return status 分类
     */
    private static Classification status(String code) {
        return new Classification("status", code, "HTTP " + code);
    }

    /**
     * @param tags Span tags
     * @return 状态码数字；无则 null
     */
    private static String httpStatusFromTags(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String raw = tags.get("http.status_code");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int code = Integer.parseInt(raw.trim());
            if (code >= 400) {
                return String.valueOf(code);
            }
        } catch (NumberFormatException ignored) {
            // ignore
        }
        return null;
    }

    /**
     * 从错误信息提取异常简单类名。
     *
     * @param message 错误信息
     * @return 简单类名；无法识别时 null
     */
    static String exceptionSimpleName(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String head = message.trim();
        int colon = head.indexOf(": ");
        if (colon > 0) {
            head = head.substring(0, colon).trim();
        }
        Matcher m = EXCEPTIONISH.matcher(head);
        if (!m.find()) {
            return null;
        }
        String fqcn = m.group(1);
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
