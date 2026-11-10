package io.github.iweidujiang.springinsight.storage;

/**
 * 当前 Span 持有量超过全量快照查询预算，拒绝依赖拓扑等重查询。
 *
 * @author 苏渡苇
 * @since 2026-11-10
 */
public class SnapshotQueryTooLargeException extends RuntimeException {

    public static final String CODE = "QUERY_TOO_LARGE";

    private final int storedSpans;
    private final int snapshotQueryMaxSpans;

    /**
     * @param storedSpans            当前持有条数
     * @param snapshotQueryMaxSpans  允许全量快照的上限
     */
    public SnapshotQueryTooLargeException(int storedSpans, int snapshotQueryMaxSpans) {
        super(String.format(
                "当前已存 %d 条 Span，超过全量查询上限 %d；请缩短时间窗、清理存储，或提高 snapshot-query-max-spans",
                storedSpans, snapshotQueryMaxSpans));
        this.storedSpans = storedSpans;
        this.snapshotQueryMaxSpans = snapshotQueryMaxSpans;
    }

    /**
     * @return 当前持有条数
     */
    public int getStoredSpans() {
        return storedSpans;
    }

    /**
     * @return 配置的全量查询上限
     */
    public int getSnapshotQueryMaxSpans() {
        return snapshotQueryMaxSpans;
    }
}
