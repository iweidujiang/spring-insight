package io.github.iweidujiang.springinsight.storage.spi;

/**
 * 单个服务在时间窗内的 Span 聚合。耗时单位为毫秒。
 *
 * @param serviceName  服务名；空串表示未填服务名
 * @param spanCount    Span 条数
 * @param errorCount   错误条数
 * @param avgDuration  平均耗时（保留两位小数）
 * @param maxDuration  最大耗时
 * @param p50Duration  中位耗时
 * @param p95Duration  p95 耗时
 * @since 2026-10-17
 */
public record ServiceAggregate(
        String serviceName,
        long spanCount,
        long errorCount,
        double avgDuration,
        long maxDuration,
        long p50Duration,
        long p95Duration
) {
}
