package io.github.iweidujiang.springinsight.server.export;

import java.util.List;
import java.util.Map;

/**
 * 错误分析服务级汇总写成 CSV（含 UTF-8 BOM）。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
public final class ErrorAnalysisCsvExporter {

    private ErrorAnalysisCsvExporter() {
    }

    /**
     * @param rows 含 service_name / total_calls / error_calls / error_rate /
     *             primary_error_type / primary_exception 的导出行
     * @return 带 BOM 的完整 CSV
     */
    public static String toCsv(List<Map<String, Object>> rows) {
        StringBuilder sb = CsvExportSupport.newCsv();
        sb.append("服务名,总调用,错误数,错误率(%),主要错误类型,主要异常类\n");
        if (rows == null) {
            return sb.toString();
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            sb.append(CsvExportSupport.escape(str(row.get("service_name")))).append(',');
            sb.append(num(row.get("total_calls"))).append(',');
            sb.append(num(row.get("error_calls"))).append(',');
            sb.append(rate(row.get("error_rate"))).append(',');
            sb.append(CsvExportSupport.escape(str(row.get("primary_error_type")))).append(',');
            sb.append(CsvExportSupport.escape(str(row.get("primary_exception")))).append('\n');
        }
        return sb.toString();
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static long num(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private static String rate(Object v) {
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        return "0";
    }
}
