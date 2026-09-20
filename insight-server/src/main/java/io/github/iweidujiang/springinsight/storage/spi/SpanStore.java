package io.github.iweidujiang.springinsight.storage.spi;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;

import java.util.List;

/**
 * Span 存储 SPI：写入与原始读取；聚合查询由 {@code TraceSpanPersistenceService} 基于 snapshot 完成。
 *
 * @since 2026-09-13
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public interface SpanStore {

    /**
     * @return 存储模式名：memory / file / sqlite
     */
    String mode();

    /**
     * 批量写入（内部应 snapshot，并做条数上限裁剪）。
     *
     * @param spans 待写入；空或 null 时忽略
     * @return 实际写入条数
     */
    int saveAll(List<TraceSpan> spans);

    /**
     * @param traceId Trace ID
     * @return 该 Trace 下 Span 快照列表（按开始时间升序）
     */
    List<TraceSpan> findByTraceId(String traceId);

    /**
     * @param lastHours 时间窗口小时；{@code <=0} 不限
     * @param limit     条数上限
     * @return 最近 Span 快照（按开始时间降序）
     */
    List<TraceSpan> findRecent(int lastHours, int limit);

    /**
     * @param serviceName 服务名
     * @param limit       条数上限
     * @return 该服务最近 Span
     */
    List<TraceSpan> findByService(String serviceName, int limit);

    /**
     * 当前全部 Span 的快照副本，供聚合查询使用。
     *
     * @return 不可与内部缓冲共享的列表
     */
    List<TraceSpan> snapshot();

    /**
     * @return 当前持有条数
     */
    int size();

    /**
     * 因条数上限或时间保留策略被裁剪的累计条数（进程内计数，重启清零）。
     *
     * @return 累计驱逐条数
     */
    default long evictedCount() {
        return 0L;
    }

    /**
     * 删除开始时间早于 cutoff 的 Span。
     *
     * @param cutoffEpochMs 截止时间戳（毫秒）
     * @return 删除条数
     */
    int purgeOlderThan(long cutoffEpochMs);

    /**
     * 清空全部 Span（file 应刷盘；sqlite 仅 DELETE，可不 VACUUM）。
     *
     * @return 删除条数
     */
    int clearAll();

    /**
     * 删除 serviceName 精确匹配的 Span（非整 Trace 级联）。
     *
     * @param serviceName 服务名；空则不删
     * @return 删除条数
     */
    int purgeByService(String serviceName);

    /**
     * 释放资源（文件刷盘、关闭 JDBC 等）。
     */
    default void close() {
        // 默认无操作
    }
}
