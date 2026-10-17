package io.github.iweidujiang.springinsight.storage.spi;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.springframework.lang.Nullable;

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
     * 按服务聚合时间窗内的 Span。不返回原始 Span。
     *
     * @param sinceEpoch 开始时间下界（毫秒，含）；{@code <=0} 表示不限
     * @param service    服务名；空则全部服务
     * @return 按服务名排序的聚合行
     */
    List<ServiceAggregate> aggregateSnapshot(long sinceEpoch, @Nullable String service);

    /**
     * 按 Trace 聚合后分页。排序取值 {@code time_desc}、{@code time_asc}、{@code duration_desc}、{@code duration_asc}。
     *
     * @param sinceEpoch    开始时间下界（毫秒，含）；{@code <=0} 表示不限
     * @param service       服务名；空则全部
     * @param status        {@code all}、{@code error} 或 {@code ok}
     * @param query         模糊匹配 Trace ID、服务名或根操作名
     * @param minDurationMs 链路耗时下限
     * @param pathPrefix    根操作路径前缀
     * @param offset        跳过的链路数
     * @param limit         本页条数，最大 200
     * @param sort          排序；无法识别时按开始时间降序
     * @return 当前页
     */
    List<TraceSummary> listTraces(long sinceEpoch, @Nullable String service, @Nullable String status,
                                  @Nullable String query, long minDurationMs, @Nullable String pathPrefix,
                                  int offset, int limit, @Nullable String sort);

    /**
     * 与 {@link #listTraces} 使用同一组筛选条件的链路总数。
     */
    long countTraces(long sinceEpoch, @Nullable String service, @Nullable String status,
                     @Nullable String query, long minDurationMs, @Nullable String pathPrefix);

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
