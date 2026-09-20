package io.github.iweidujiang.springinsight.server.ai;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 进程内 AI 调用审计（最近 N 条；重启清空）。
 *
 * @since 2026-09-20
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Component
public class InsightAiAuditLog {

    private static final int MAX = 100;
    private final ConcurrentLinkedDeque<Map<String, Object>> entries = new ConcurrentLinkedDeque<>();

    /**
     * 记录一次调用。
     *
     * @param kind     trace | errors | dependency
     * @param subject  traceId / hours / edge
     * @param degraded 是否降级
     * @param model    模型名
     */
    public void record(String kind, String subject, boolean degraded, String model) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("at", Instant.now().toString());
        row.put("kind", kind != null ? kind : "");
        row.put("subject", subject != null ? subject : "");
        row.put("degraded", degraded);
        row.put("model", model != null ? model : "");
        entries.addFirst(row);
        while (entries.size() > MAX) {
            entries.removeLast();
        }
    }

    /**
     * @param limit 条数上限
     * @return 最近记录（新→旧）
     */
    public List<Map<String, Object>> recent(int limit) {
        int max = Math.max(1, Math.min(limit, MAX));
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> e : entries) {
            if (i++ >= max) {
                break;
            }
            out.add(e);
        }
        return Collections.unmodifiableList(out);
    }
}
