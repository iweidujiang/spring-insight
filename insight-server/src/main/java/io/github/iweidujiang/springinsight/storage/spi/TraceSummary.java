package io.github.iweidujiang.springinsight.storage.spi;

/**
 * 一条链路（按 Trace ID 聚合）的列表行。耗时单位为毫秒。
 *
 * @param traceId       Trace ID
 * @param serviceName   根 Span 的服务名
 * @param operationName 根 Span 的操作名
 * @param startTime     最早开始时间（毫秒）
 * @param durationMs    整条链路耗时
 * @param spanCount     Span 条数
 * @param serviceCount  涉及的服务数
 * @param hasError      是否含错误 Span
 * @param statusCode    {@code ERROR} 或 {@code OK}
 * @since 2026-10-17
 */
public record TraceSummary(
        String traceId,
        String serviceName,
        String operationName,
        long startTime,
        long durationMs,
        int spanCount,
        int serviceCount,
        boolean hasError,
        String statusCode
) {
}
