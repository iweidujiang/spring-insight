package io.github.iweidujiang.springinsight.server.export;

/**
 * CSV 共用工具：UTF-8 BOM 与 RFC 4180 字段转义（便于 Excel 打开）。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
public final class CsvExportSupport {

    /** Excel 识别 UTF-8 用的 BOM */
    public static final char UTF8_BOM = '\uFEFF';

    private CsvExportSupport() {
    }

    /**
     * 新建带 BOM 的 CSV 缓冲。
     *
     * @return 已写入 BOM 的 {@link StringBuilder}
     */
    public static StringBuilder newCsv() {
        return new StringBuilder().append(UTF8_BOM);
    }

    /**
     * RFC 4180：含逗号、引号或换行时用双引号包裹，内部引号加倍。
     *
     * @param value 单元格原文
     * @return 已转义的字段
     */
    public static String escape(String value) {
        String text = value == null ? "" : value;
        boolean needQuote = text.indexOf(',') >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0;
        if (!needQuote) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
