package io.github.iweidujiang.springinsight.server.export;

import io.github.iweidujiang.springinsight.storage.spi.TraceSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 链路 CSV：BOM、表头、转义与列顺序。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
class TraceCsvExporterTest {

    @Test
    void csvStartsWithBomAndHeader() {
        String csv = TraceCsvExporter.toCsv(List.of());
        assertTrue(csv.startsWith(String.valueOf(TraceCsvExporter.UTF8_BOM)));
        assertTrue(csv.contains("TraceId,根操作名,服务名,耗时(ms),状态,Span数,时间"));
    }

    @Test
    void csvEscapesCommaQuoteAndNewline() {
        TraceSummary row = new TraceSummary(
                "t-1",
                "order,svc",
                "GET \"/a\"\n/b",
                1_700_000_000_000L,
                42L,
                3,
                1,
                false,
                "OK");
        String csv = TraceCsvExporter.toCsv(List.of(row));
        assertTrue(csv.contains("\"order,svc\""));
        assertTrue(csv.contains("\"GET \"\"/a\"\"\n/b\""));
        assertTrue(csv.contains(",42,OK,3,"));
        assertTrue(csv.contains("t-1,"));
    }

    @Test
    void escapeOnlyQuotesWhenNeeded() {
        assertEquals("plain", TraceCsvExporter.escape("plain"));
        assertEquals("\"a,b\"", TraceCsvExporter.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", TraceCsvExporter.escape("say \"hi\""));
        assertEquals("\"a\nb\"", TraceCsvExporter.escape("a\nb"));
    }
}
