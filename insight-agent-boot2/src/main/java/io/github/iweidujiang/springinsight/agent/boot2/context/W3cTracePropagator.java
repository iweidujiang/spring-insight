package io.github.iweidujiang.springinsight.agent.boot2.context;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W3C Trace Context（{@code traceparent}）编解码，供跨服务 HTTP 透传复用（Boot2 / Java 8）。
 * <p>
 * 格式：{@code 00-{32hex-trace-id}-{16hex-parent-id}-{2hex-flags}}。
 * </p>
 *
 * @since 2026-09-29
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class W3cTracePropagator {

    /** W3C 标准请求头名 */
    public static final String TRACEPARENT_HEADER = "traceparent";

    private static final String VERSION = "00";
    private static final String FLAGS_SAMPLED = "01";

    private static final Pattern TRACEPARENT = Pattern.compile(
            "^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$",
            Pattern.CASE_INSENSITIVE);

    private static final String ALL_ZERO_TRACE = "00000000000000000000000000000000";
    private static final String ALL_ZERO_SPAN = "0000000000000000";

    /**
     * 工具类，禁止实例化。
     */
    private W3cTracePropagator() {
    }

    /**
     * 将当前 Span 上下文编码为 {@code traceparent} 值。
     *
     * @param traceId TraceId
     * @param spanId  SpanId
     * @return 可写入请求头的字符串；入参无效时 empty
     */
    public static Optional<String> formatTraceparent(String traceId, String spanId) {
        String tid = normalizeHexId(traceId, 32);
        String sid = normalizeHexId(spanId, 16);
        if (tid == null || sid == null) {
            return Optional.empty();
        }
        if (ALL_ZERO_TRACE.equals(tid) || ALL_ZERO_SPAN.equals(sid)) {
            return Optional.empty();
        }
        return Optional.of(VERSION + "-" + tid + "-" + sid + "-" + FLAGS_SAMPLED);
    }

    /**
     * 解析 {@code traceparent} 头。
     *
     * @param headerValue 原始头值，可为 null
     * @return 解析成功时的远程上下文；失败 empty
     */
    public static Optional<RemoteContext> parseTraceparent(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return Optional.empty();
        }
        Matcher m = TRACEPARENT.matcher(headerValue.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        String version = m.group(1).toLowerCase(Locale.ROOT);
        if (!VERSION.equals(version)) {
            return Optional.empty();
        }
        String traceId = m.group(2).toLowerCase(Locale.ROOT);
        String parentId = m.group(3).toLowerCase(Locale.ROOT);
        if (ALL_ZERO_TRACE.equals(traceId) || ALL_ZERO_SPAN.equals(parentId)) {
            return Optional.empty();
        }
        return Optional.of(new RemoteContext(traceId, parentId));
    }

    /**
     * 从 getter 读取 {@code traceparent} 并解析。
     *
     * @param headerGetter 头名 → 头值
     * @return 远程上下文；无头或非法时 empty
     */
    public static Optional<RemoteContext> extract(HeaderGetter headerGetter) {
        if (headerGetter == null) {
            return Optional.empty();
        }
        return parseTraceparent(headerGetter.get(TRACEPARENT_HEADER));
    }

    /**
     * 将 Span 上下文写入 setter。
     *
     * @param traceId      TraceId
     * @param spanId       当前 SpanId（下游 parent）
     * @param headerSetter 头写入回调
     * @return 是否成功写入
     */
    public static boolean inject(String traceId, String spanId, HeaderSetter headerSetter) {
        if (headerSetter == null) {
            return false;
        }
        Optional<String> value = formatTraceparent(traceId, spanId);
        if (!value.isPresent()) {
            return false;
        }
        headerSetter.set(TRACEPARENT_HEADER, value.get());
        return true;
    }

    /**
     * 将任意十六进制 ID 规范到固定长度（不足左补 0，过长取右侧）。
     *
     * @param raw    原始 ID
     * @param length 目标长度
     * @return 小写十六进制；无法规范化时 null
     */
    static String normalizeHexId(String raw, int length) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String hex = raw.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return null;
            }
        }
        if (hex.length() == length) {
            return hex;
        }
        if (hex.length() < length) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = hex.length(); i < length; i++) {
                sb.append('0');
            }
            sb.append(hex);
            return sb.toString();
        }
        return hex.substring(hex.length() - length);
    }

    /**
     * 读请求头。
     */
    public interface HeaderGetter {
        /**
         * @param name 头名
         * @return 头值，可为 null
         */
        String get(String name);
    }

    /**
     * 写请求头。
     */
    public interface HeaderSetter {
        /**
         * @param name  头名
         * @param value 头值
         */
        void set(String name, String value);
    }

    /**
     * 入站解析得到的远程 Trace 上下文。
     */
    public static final class RemoteContext {
        private final String traceId;
        private final String parentSpanId;

        /**
         * @param traceId      远程 TraceId
         * @param parentSpanId 远端 SpanId
         */
        public RemoteContext(String traceId, String parentSpanId) {
            this.traceId = traceId;
            this.parentSpanId = parentSpanId;
        }

        /**
         * @return TraceId
         */
        public String getTraceId() {
            return traceId;
        }

        /**
         * @return parent SpanId
         */
        public String getParentSpanId() {
            return parentSpanId;
        }
    }
}
