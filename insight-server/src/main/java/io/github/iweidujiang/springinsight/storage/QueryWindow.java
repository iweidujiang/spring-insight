package io.github.iweidujiang.springinsight.storage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 观测查询时间窗：滚动近 N 小时，或绝对半开区间 {@code [from, to)}。
 *
 * @author 苏渡苇
 * @since 2026-11-15
 */
public final class QueryWindow {

    private final long fromInclusiveMs;
    private final long toExclusiveMs;
    /** 滚动窗小时；绝对窗时为 {@code null}；{@code 0} 表示全部已存 */
    private final Integer hours;

    private QueryWindow(long fromInclusiveMs, long toExclusiveMs, Integer hours) {
        this.fromInclusiveMs = Math.max(0L, fromInclusiveMs);
        this.toExclusiveMs = Math.max(0L, toExclusiveMs);
        this.hours = hours;
    }

    /**
     * 滚动近 N 小时；{@code hours <= 0} 表示不设下界（全部已存）。
     *
     * @param hours 小时数
     * @return 时间窗
     */
    public static QueryWindow ofHours(int hours) {
        if (hours <= 0) {
            return new QueryWindow(0L, 0L, 0);
        }
        long from = Instant.now().minus(hours, ChronoUnit.HOURS).toEpochMilli();
        return new QueryWindow(from, 0L, hours);
    }

    /**
     * 绝对区间；无效参数时回退为近 24 小时。
     *
     * @param fromInclusiveMs 起始（含）
     * @param toExclusiveMs   结束（不含）；{@code <=0} 表示不设上界
     * @return 时间窗
     */
    public static QueryWindow ofAbsolute(long fromInclusiveMs, long toExclusiveMs) {
        long from = Math.max(0L, fromInclusiveMs);
        long to = Math.max(0L, toExclusiveMs);
        if (from <= 0L && to <= 0L) {
            return ofHours(24);
        }
        if (to > 0L && from > 0L && to <= from) {
            return ofHours(24);
        }
        return new QueryWindow(from, to, null);
    }

    /**
     * 解析请求参数：有效 {@code from}/{@code to} 优先，否则用 {@code hours}。
     *
     * @param hours  滚动小时；可为 null（默认 24）
     * @param fromMs 绝对起点 epoch ms
     * @param toMs   绝对终点 epoch ms（不含）
     * @return 时间窗
     */
    public static QueryWindow resolve(Integer hours, Long fromMs, Long toMs) {
        long from = fromMs == null ? 0L : fromMs;
        long to = toMs == null ? 0L : toMs;
        if (from > 0L || to > 0L) {
            if (from > 0L && to > 0L && to > from) {
                return ofAbsolute(from, to);
            }
            if (from > 0L) {
                return ofAbsolute(from, 0L);
            }
            // 仅 to：取 to 之前 24h
            return ofAbsolute(Math.max(0L, to - 24L * 3_600_000L), to);
        }
        int h = hours == null ? 24 : hours;
        return ofHours(h);
    }

    /**
     * Span 开始时间是否落在窗内。
     *
     * @param startTimeMs 开始时刻
     * @return 是否包含
     */
    public boolean contains(long startTimeMs) {
        if (fromInclusiveMs > 0L && startTimeMs < fromInclusiveMs) {
            return false;
        }
        if (toExclusiveMs > 0L && startTimeMs >= toExclusiveMs) {
            return false;
        }
        return true;
    }

    /**
     * @return 下界（含）；0 表示不限
     */
    public long fromInclusiveMs() {
        return fromInclusiveMs;
    }

    /**
     * @return 上界（不含）；0 表示不限
     */
    public long toExclusiveMs() {
        return toExclusiveMs;
    }

    /**
     * @return 滚动小时；绝对窗为 null
     */
    public Integer hours() {
        return hours;
    }

    /**
     * 是否为绝对时间窗。
     *
     * @return true 表示 from/to 模式
     */
    public boolean isAbsolute() {
        return hours == null;
    }

    /**
     * 响应体兼容字段：绝对窗回传 -1，否则回传小时。
     *
     * @return hours 或 -1
     */
    public int hoursEcho() {
        return hours == null ? -1 : hours;
    }
}
