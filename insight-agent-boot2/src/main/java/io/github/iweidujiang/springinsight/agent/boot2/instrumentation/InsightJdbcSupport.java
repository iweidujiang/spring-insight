package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.springframework.util.StringUtils;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JDBC CLIENT Span 辅助：SQL 类型、截断、慢阈值、上报（不压入 Trace 栈）。
 *
 * @since 2026-10-11
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class InsightJdbcSupport {

    private static final int SQL_MAX_LEN = 512;
    private static final Pattern SQL_TYPE_PATTERN = Pattern.compile(
            "^(SELECT|INSERT|UPDATE|DELETE|EXECUTE|CALL|MERGE|CREATE|ALTER|DROP|TRUNCATE|"
                    + "GRANT|REVOKE|COMMIT|ROLLBACK|SAVEPOINT|LOCK|UNLOCK)\\b",
            Pattern.CASE_INSENSITIVE);

    private InsightJdbcSupport() {
    }

    /**
     * @param sql 原始 SQL
     * @return 大写语句类型；无法识别时为 {@code UNKNOWN}
     */
    public static String sqlType(String sql) {
        if (!StringUtils.hasText(sql)) {
            return "UNKNOWN";
        }
        Matcher matcher = SQL_TYPE_PATTERN.matcher(sql.trim());
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return "UNKNOWN";
    }

    /**
     * @param sql 原始 SQL
     * @return 截断后的 SQL（空白折叠为单空格）
     */
    public static String truncateSql(String sql) {
        if (sql == null) {
            return "";
        }
        String compact = sql.replaceAll("\\s+", " ").trim();
        if (compact.length() <= SQL_MAX_LEN) {
            return compact;
        }
        return compact.substring(0, SQL_MAX_LEN) + "…";
    }

    /**
     * @param properties 配置
     * @param durationMs 耗时
     * @param error      是否异常结束
     * @return 是否应上报
     */
    public static boolean shouldReport(InsightBoot2Properties properties, long durationMs, boolean error) {
        if (error) {
            return true;
        }
        long threshold = properties.getJdbcSlowThresholdMs();
        if (threshold <= 0L) {
            return true;
        }
        return durationMs >= threshold;
    }

    /**
     * 执行 JDBC 调用并按需上报 CLIENT Span（无父 Span 时直接执行、不上报）。
     *
     * @param sql      SQL 文本（可空）
     * @param remote   可选 remoteService（如 JDBC URL host）
     * @param props    配置
     * @param listener 上报
     * @param action   实际 JDBC 调用
     * @param <T>      返回类型
     * @return 调用结果
     * @throws Exception 透传 SQLException 等
     */
    public static <T> T traceExecute(String sql,
                                     String remote,
                                     InsightBoot2Properties props,
                                     SpanReportingListener listener,
                                     SqlCallable<T> action) throws Exception {
        if (props == null || !props.isEnabled() || !props.isJdbcTracingEnabled()) {
            return action.call();
        }
        Optional<TraceSpan> parentOpt = TraceContext.currentSpan();
        if (!parentOpt.isPresent()) {
            return action.call();
        }

        TraceSpan parent = parentOpt.get();
        String type = sqlType(sql);
        String truncated = truncateSql(sql);
        TraceSpan span = new TraceSpan(parent.getTraceId(), parent.getSpanId());
        span.setSampled(parent.isSampled());
        span.setSpanKind("CLIENT");
        span.setComponent("JDBC");
        span.setOperationName("DB " + type);
        if (StringUtils.hasText(remote)) {
            span.setRemoteService(remote);
        }
        if (StringUtils.hasText(truncated)) {
            span.setRemoteEndpoint(truncated.length() > 120 ? truncated.substring(0, 120) + "…" : truncated);
            span.addTag("db.sql", truncated);
        }
        span.addTag("db.sql_type", type);

        long start = System.currentTimeMillis();
        try {
            T result = action.call();
            long duration = Math.max(0L, System.currentTimeMillis() - start);
            if (!shouldReport(props, duration, false)) {
                return result;
            }
            if (duration >= props.getJdbcSlowThresholdMs() && props.getJdbcSlowThresholdMs() > 0L) {
                span.addTag("db.slow", "true");
            }
            span.finish();
            report(listener, span);
            return result;
        } catch (SQLException e) {
            span.finish("SQL_ERROR", e.getMessage());
            report(listener, span);
            throw e;
        } catch (Exception e) {
            span.finish("DB_ERROR", e.getMessage());
            report(listener, span);
            throw e;
        }
    }

    private static void report(SpanReportingListener listener, TraceSpan span) {
        if (listener != null && span.isSampled()) {
            listener.reportSpan(TraceSpan.snapshot(span));
        }
    }

    /**
     * 可抛受检异常的 JDBC 调用。
     *
     * @param <T> 返回类型
     */
    @FunctionalInterface
    public interface SqlCallable<T> {
        /**
         * @return 调用结果
         * @throws Exception JDBC 或业务异常
         */
        T call() throws Exception;
    }
}
