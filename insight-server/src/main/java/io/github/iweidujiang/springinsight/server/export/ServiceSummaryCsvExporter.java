package io.github.iweidujiang.springinsight.server.export;

import java.util.List;
import java.util.Map;

/**
 * 服务延迟汇总写成 CSV（含 UTF-8 BOM）。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
public final class ServiceSummaryCsvExporter {

    private ServiceSummaryCsvExporter() {
    }

    /**
     * @param rows {@link io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService#getServiceLatencySummaries}
     *             同行字段
     * @return 带 BOM 的完整 CSV
     */
    public static String toCsv(List<Map<String, Object>> rows) {
        StringBuilder sb = CsvExportSupport.newCsv();
        sb.append("服务名,Span数,错误数,错误率(%),平均耗时(ms),p95耗时(ms),最大耗时(ms)\n");
        if (rows == null) {
            return sb.toString();
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            sb.append(CsvExportSupport.escape(str(row.get("service_name")))).append(',');
            sb.append(num(row.get("span_count"))).append(',');
            sb.append(num(row.get("error_count"))).append(',');
            sb.append(rate(row.get("error_rate"))).append(',');
            sb.append(rate(row.get("avg_ms"))).append(',');
            sb.append(num(row.get("p95_ms"))).append(',');
            sb.append(num(row.get("max_ms"))).append('\n');
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
