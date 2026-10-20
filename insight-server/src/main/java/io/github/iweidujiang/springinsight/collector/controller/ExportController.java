package io.github.iweidujiang.springinsight.collector.controller;

import io.github.iweidujiang.springinsight.server.export.ErrorAnalysisCsvExporter;
import io.github.iweidujiang.springinsight.server.export.InsightDashboardExportService;
import io.github.iweidujiang.springinsight.server.export.ServiceSummaryCsvExporter;
import io.github.iweidujiang.springinsight.server.export.TraceCsvExporter;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import io.github.iweidujiang.springinsight.storage.spi.TraceSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 控制台数据导出（链路 / 错误分析 / 服务汇总 CSV，以及仪表盘 JSON 快照）。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ui/export")
@RequiredArgsConstructor
@Tag(name = "Export", description = "控制台导出")
public class ExportController {

    /** 与列表分页一致的每批条数 */
    private static final int PAGE_SIZE = 200;

    /** 单次导出上限，避免一次拉爆内存 */
    private static final int MAX_ROWS = 10_000;

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private static final MediaType CSV_UTF8 =
            new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final SpanStore spanStore;
    private final TraceSpanPersistenceService persistenceService;
    private final InsightDashboardExportService dashboardExportService;

    /**
     * 导出时间窗内的链路列表为 CSV。筛选条件与最近链路列表一致。
     *
     * @param hours   时间窗小时；默认 1。小于等于 0 表示不设下界
     * @param service 可选服务名
     * @param status  {@code ALL} / {@code ERROR} / {@code OK}（大小写不敏感）
     * @param format  目前仅支持 {@code csv}
     * @return CSV 附件
     */
    @GetMapping("/traces")
    @Operation(summary = "导出链路列表 CSV")
    public ResponseEntity<?> exportTraces(
            @RequestParam(value = "hours", defaultValue = "1") int hours,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "status", defaultValue = "ALL") String status,
            @RequestParam(value = "format", defaultValue = "csv") String format) {

        if (!isCsv(format)) {
            return ResponseEntity.badRequest().body(Map.of("message", "目前仅支持 format=csv"));
        }

        try {
            long since = hours <= 0 ? 0L : Instant.now().minus(hours, ChronoUnit.HOURS).toEpochMilli();
            String normalizedStatus = status == null || status.isBlank() ? "all" : status.trim().toLowerCase();
            List<TraceSummary> rows = loadTraces(since, service, normalizedStatus);
            return csvAttachment(TraceCsvExporter.toCsv(rows), "traces");
        } catch (Exception e) {
            log.error("导出链路 CSV 失败", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "导出失败"));
        }
    }

    /**
     * 导出错误分析服务级汇总 CSV。
     *
     * @param hours  时间窗小时；默认 1。小于等于 0 表示不设下界
     * @param format 目前仅支持 {@code csv}
     * @return CSV 附件
     */
    @GetMapping("/errors")
    @Operation(summary = "导出错误分析 CSV")
    public ResponseEntity<?> exportErrors(
            @RequestParam(value = "hours", defaultValue = "1") int hours,
            @RequestParam(value = "format", defaultValue = "csv") String format) {

        if (!isCsv(format)) {
            return ResponseEntity.badRequest().body(Map.of("message", "目前仅支持 format=csv"));
        }

        try {
            List<Map<String, Object>> rows = persistenceService.findErrorAnalysisExportRows(hours);
            return csvAttachment(ErrorAnalysisCsvExporter.toCsv(rows), "errors");
        } catch (Exception e) {
            log.error("导出错误分析 CSV 失败", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "导出失败"));
        }
    }

    /**
     * 导出服务延迟汇总 CSV。
     *
     * @param hours  时间窗小时；默认 1。小于等于 0 表示不设下界
     * @param format 目前仅支持 {@code csv}
     * @return CSV 附件
     */
    @GetMapping("/services")
    @Operation(summary = "导出服务汇总 CSV")
    public ResponseEntity<?> exportServices(
            @RequestParam(value = "hours", defaultValue = "1") int hours,
            @RequestParam(value = "format", defaultValue = "csv") String format) {

        if (!isCsv(format)) {
            return ResponseEntity.badRequest().body(Map.of("message", "目前仅支持 format=csv"));
        }

        try {
            List<Map<String, Object>> rows =
                    persistenceService.getServiceLatencySummaries(hours, MAX_ROWS);
            return csvAttachment(ServiceSummaryCsvExporter.toCsv(rows), "services");
        } catch (Exception e) {
            log.error("导出服务汇总 CSV 失败", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "导出失败"));
        }
    }

    /**
     * 导出仪表盘快照 JSON（KPI、摘要、Top 列表等）。
     *
     * @param hours  时间窗小时；默认 1。小于等于 0 表示全部已存
     * @param format 目前仅支持 {@code json}
     * @return JSON 附件
     */
    @GetMapping("/dashboard")
    @Operation(summary = "导出仪表盘快照 JSON")
    public ResponseEntity<?> exportDashboard(
            @RequestParam(value = "hours", defaultValue = "1") int hours,
            @RequestParam(value = "format", defaultValue = "json") String format) {

        if (!"json".equalsIgnoreCase(format == null ? "" : format.trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "目前仅支持 format=json"));
        }

        try {
            Map<String, Object> body = dashboardExportService.buildSnapshot(hours);
            String filename = "dashboard-" + FILE_STAMP.format(Instant.now()) + ".json";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(body);
        } catch (Exception e) {
            log.error("导出仪表盘 JSON 失败", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "导出失败"));
        }
    }

    /**
     * 按页调用 {@link SpanStore#listTraces}，凑齐导出数据。
     */
    private List<TraceSummary> loadTraces(long since, String service, String status) {
        List<TraceSummary> all = new ArrayList<>();
        int offset = 0;
        while (all.size() < MAX_ROWS) {
            List<TraceSummary> page = spanStore.listTraces(
                    since, service, status, null, 0L, null, offset, PAGE_SIZE, "time_desc");
            if (page == null || page.isEmpty()) {
                break;
            }
            int room = MAX_ROWS - all.size();
            if (page.size() > room) {
                all.addAll(page.subList(0, room));
                break;
            }
            all.addAll(page);
            if (page.size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }
        return all;
    }

    private static boolean isCsv(String format) {
        return "csv".equalsIgnoreCase(format == null ? "" : format.trim());
    }

    private static ResponseEntity<byte[]> csvAttachment(String csv, String prefix) {
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        String filename = prefix + "-" + FILE_STAMP.format(Instant.now()) + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
