package io.github.iweidujiang.springinsight.server.export;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务汇总 CSV：BOM、表头与数值列。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
class ServiceSummaryCsvExporterTest {

    @Test
    void csvStartsWithBomAndHeader() {
        String csv = ServiceSummaryCsvExporter.toCsv(List.of());
        assertTrue(csv.startsWith(String.valueOf(CsvExportSupport.UTF8_BOM)));
        assertTrue(csv.contains("服务名,Span数,错误数,错误率(%),平均耗时(ms),p95耗时(ms),最大耗时(ms)"));
    }

    @Test
    void csvWritesLatencyRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("service_name", "pay-svc");
        row.put("span_count", 50);
        row.put("error_count", 2);
        row.put("error_rate", 4.0);
        row.put("avg_ms", 12.5);
        row.put("p95_ms", 40L);
        row.put("max_ms", 90L);

        String csv = ServiceSummaryCsvExporter.toCsv(List.of(row));
        assertTrue(csv.contains("pay-svc,50,2,4,12.5,40,90"));
    }
}
