package io.github.iweidujiang.springinsight.server.export;

import io.github.iweidujiang.springinsight.storage.spi.TraceSummary;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 把链路摘要写成 CSV 文本（含 UTF-8 BOM，便于 Excel 打开）。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
public final class TraceCsvExporter {

    /** Excel 识别 UTF-8 用的 BOM（委托 {@link CsvExportSupport}） */
    public static final char UTF8_BOM = CsvExportSupport.UTF8_BOM;

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private TraceCsvExporter() {
    }

    /**
     * @param rows 链路摘要
     * @return 带 BOM 的完整 CSV
     */
    public static String toCsv(List<TraceSummary> rows) {
        StringBuilder sb = CsvExportSupport.newCsv();
        sb.append("TraceId,根操作名,服务名,耗时(ms),状态,Span数,时间\n");
        if (rows == null) {
            return sb.toString();
        }
        for (TraceSummary row : rows) {
            if (row == null) {
                continue;
            }
            sb.append(CsvExportSupport.escape(row.traceId())).append(',');
            sb.append(CsvExportSupport.escape(row.operationName())).append(',');
            sb.append(CsvExportSupport.escape(row.serviceName())).append(',');
            sb.append(row.durationMs()).append(',');
            sb.append(CsvExportSupport.escape(statusLabel(row))).append(',');
            sb.append(row.spanCount()).append(',');
            sb.append(CsvExportSupport.escape(formatTime(row.startTime()))).append('\n');
        }
        return sb.toString();
    }

    /**
     * @param value 单元格原文
     * @return 已转义的字段
     * @see CsvExportSupport#escape(String)
     */
    public static String escape(String value) {
        return CsvExportSupport.escape(value);
    }

    /**
     * @param epochMs 开始时间
     * @return 本地可读时间；无效时为空串
     */
    public static String formatTime(long epochMs) {
        if (epochMs <= 0L) {
            return "";
        }
        return TIME.format(Instant.ofEpochMilli(epochMs));
    }

    private static String statusLabel(TraceSummary row) {
        if (row.statusCode() != null && !row.statusCode().isBlank()) {
            return row.statusCode();
        }
        return row.hasError() ? "ERROR" : "OK";
    }
}
