package io.github.iweidujiang.springinsight.server.export;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误分析 CSV：BOM、表头与字段转义。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
class ErrorAnalysisCsvExporterTest {

    @Test
    void csvStartsWithBomAndHeader() {
        String csv = ErrorAnalysisCsvExporter.toCsv(List.of());
        assertTrue(csv.startsWith(String.valueOf(CsvExportSupport.UTF8_BOM)));
        assertTrue(csv.contains("服务名,总调用,错误数,错误率(%),主要错误类型,主要异常类"));
    }

    @Test
    void csvWritesRowAndEscapesServiceName() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("service_name", "order,svc");
        row.put("total_calls", 100);
        row.put("error_calls", 5);
        row.put("error_rate", 5.0);
        row.put("primary_error_type", "HTTP 500");
        row.put("primary_exception", "NullPointerException");

        String csv = ErrorAnalysisCsvExporter.toCsv(List.of(row));
        assertTrue(csv.contains("\"order,svc\""));
        assertTrue(csv.contains(",100,5,5,HTTP 500,NullPointerException"));
    }
}
