package io.github.iweidujiang.springinsight.server.alert;

import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettings;
import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 告警静默规则 CRUD（写入 runtime-settings.json）。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
@Service
@RequiredArgsConstructor
public class InsightAlertSilenceService {

    private final InsightRuntimeSettingsService settingsService;

    /**
     * @return 全部静默规则副本
     */
    public List<SilenceRule> list() {
        return copyAll(currentSilences());
    }

    /**
     * 创建规则；自动生成 id。
     *
     * @param incoming 请求体
     * @return 规范化后的规则
     */
    public SilenceRule create(SilenceRule incoming) {
        SilenceRule normalized = normalize(incoming, true);
        InsightRuntimeSettings merged = settingsService.snapshot();
        ensureAlert(merged);
        List<SilenceRule> rules = copyAll(merged.getAlert().getSilences());
        rules.add(normalized);
        merged.getAlert().setSilences(rules);
        settingsService.replaceSettings(merged);
        return copyOf(normalized);
    }

    /**
     * @param id 规则 id
     * @return 是否删除成功
     */
    public boolean delete(String id) {
        String key = id == null ? "" : id.trim();
        if (!StringUtils.hasText(key)) {
            return false;
        }
        InsightRuntimeSettings merged = settingsService.snapshot();
        ensureAlert(merged);
        List<SilenceRule> rules = copyAll(merged.getAlert().getSilences());
        boolean removed = rules.removeIf(r -> key.equals(r.getId()));
        if (!removed) {
            return false;
        }
        merged.getAlert().setSilences(rules);
        settingsService.replaceSettings(merged);
        return true;
    }

    /**
     * @return 供调度器读取的当前规则列表
     */
    public List<SilenceRule> effectiveRules() {
        return list();
    }

    /**
     * @param rule 规则
     * @return API Map
     */
    public static Map<String, Object> toMap(SilenceRule rule) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", nullToEmpty(rule.getId()));
        body.put("serviceName", nullToEmpty(rule.getServiceName()));
        body.put("metric", nullToEmpty(rule.getMetric()));
        body.put("startTime", nullToEmpty(rule.getStartTime()));
        body.put("endTime", nullToEmpty(rule.getEndTime()));
        body.put("timezone", nullToEmpty(rule.getTimezone()));
        body.put("reason", nullToEmpty(rule.getReason()));
        return body;
    }

    /**
     * @param incoming   原始
     * @param generateId 是否生成新 id
     * @return 规范化副本
     */
    public static SilenceRule normalize(SilenceRule incoming, boolean generateId) {
        if (incoming == null) {
            throw new IllegalArgumentException("静默规则不能为空");
        }
        SilenceRule out = new SilenceRule();
        if (generateId || !StringUtils.hasText(incoming.getId())) {
            out.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        } else {
            out.setId(incoming.getId().trim());
        }
        out.setServiceName(nullToEmpty(incoming.getServiceName()).trim());
        String metric = nullToEmpty(incoming.getMetric()).trim().toLowerCase();
        out.setMetric(metric);
        String start = StringUtils.hasText(incoming.getStartTime()) ? incoming.getStartTime().trim() : "00:00";
        String end = StringUtils.hasText(incoming.getEndTime()) ? incoming.getEndTime().trim() : "00:00";
        if (InsightAlertEvaluator.parseHhMm(start) == null || InsightAlertEvaluator.parseHhMm(end) == null) {
            throw new IllegalArgumentException("startTime / endTime 须为 HH:mm");
        }
        out.setStartTime(start);
        out.setEndTime(end);
        out.setTimezone(nullToEmpty(incoming.getTimezone()).trim());
        out.setReason(nullToEmpty(incoming.getReason()).trim());
        return out;
    }

    private List<SilenceRule> currentSilences() {
        InsightRuntimeSettings snap = settingsService.snapshot();
        if (snap == null || snap.getAlert() == null || snap.getAlert().getSilences() == null) {
            return List.of();
        }
        return snap.getAlert().getSilences();
    }

    private static void ensureAlert(InsightRuntimeSettings settings) {
        if (settings.getAlert() == null) {
            settings.setAlert(new InsightRuntimeSettings.AlertSettings());
        }
    }

    private static List<SilenceRule> copyAll(List<SilenceRule> source) {
        List<SilenceRule> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        for (SilenceRule rule : source) {
            if (rule != null) {
                out.add(copyOf(rule));
            }
        }
        return out;
    }

    private static SilenceRule copyOf(SilenceRule rule) {
        SilenceRule c = new SilenceRule();
        c.setId(rule.getId());
        c.setServiceName(rule.getServiceName());
        c.setMetric(rule.getMetric());
        c.setStartTime(rule.getStartTime());
        c.setEndTime(rule.getEndTime());
        c.setTimezone(rule.getTimezone());
        c.setReason(rule.getReason());
        return c;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
