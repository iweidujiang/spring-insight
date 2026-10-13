package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.server.ServerVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脱敏排障包：Trace Context + Server 版本 + 时段窗摘要（不调模型）。
 *
 * @since 2026-10-13
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Service
public class TroubleshootPackExportService {

    /** 排障包 schema；与 Trace Context 独立编号 */
    public static final String SCHEMA_VERSION = "1";

    private static final int WINDOW_TOP_N = 8;

    private final TraceContextExportService traceContextExportService;
    private final TraceSpanPersistenceService traceSpanPersistenceService;
    private final Environment environment;
    private final BuildProperties buildProperties;

    /**
     * @param traceContextExportService Trace Context
     * @param traceSpanPersistenceService 窗摘要
     * @param environment                 配置
     * @param buildProperties             build-info，可空
     */
    @Autowired
    public TroubleshootPackExportService(TraceContextExportService traceContextExportService,
                                         TraceSpanPersistenceService traceSpanPersistenceService,
                                         Environment environment,
                                         @Autowired(required = false) BuildProperties buildProperties) {
        this.traceContextExportService = traceContextExportService;
        this.traceSpanPersistenceService = traceSpanPersistenceService;
        this.environment = environment;
        this.buildProperties = buildProperties;
    }

    /**
     * 按 Trace ID 构建排障包；无 Span 时返回 {@code null}。
     *
     * @param traceId Trace ID
     * @param hours   窗摘要小时数；{@code <=0} 表示全部已存
     * @return 排障包 Map，或 null（未找到 Trace）
     */
    public Map<String, Object> buildPack(String traceId, int hours) {
        Map<String, Object> trace = traceContextExportService.buildContext(traceId);
        if (trace == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        long fromMs = hours <= 0 ? 0L : Math.max(0L, now - hours * 3_600_000L);
        long toMsExclusive = hours <= 0 ? 0L : now + 1L;
        Map<String, Object> stats = traceSpanPersistenceService.summarizeWindow(fromMs, toMsExclusive, WINDOW_TOP_N);

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("version", ServerVersion.resolve(environment, buildProperties));

        Map<String, Object> window = new LinkedHashMap<>();
        window.put("hours", hours);
        window.put("fromMs", fromMs > 0L ? fromMs : null);
        window.put("toMs", hours <= 0 ? null : now);
        window.put("spanCount", stats.get("spanCount"));
        window.put("errorSpanCount", stats.get("errorSpanCount"));
        window.put("errorServices", stats.get("errorServices"));
        window.put("slowServices", stats.get("slowServices"));
        window.put("hotEdges", stats.get("hotEdges"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", SCHEMA_VERSION);
        body.put("kind", "troubleshoot-pack");
        body.put("exportedAt", Instant.ofEpochMilli(now).toString());
        body.put("server", server);
        body.put("window", window);
        body.put("trace", trace);
        return body;
    }
}
