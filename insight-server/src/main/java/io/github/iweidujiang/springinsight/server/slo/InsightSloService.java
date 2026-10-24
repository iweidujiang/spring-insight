package io.github.iweidujiang.springinsight.server.slo;

import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettings;
import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettingsService;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SLO 配置 CRUD（写入 runtime-settings.json）与错误预算查询。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
@Service
@RequiredArgsConstructor
public class InsightSloService {

    private final InsightRuntimeSettingsService settingsService;
    private final TraceSpanPersistenceService persistenceService;

    /**
     * @return 全部 SLO 配置副本
     */
    public List<ServiceSlo> list() {
        return copyAll(settingsService.snapshot().getSlos());
    }

    /**
     * @param serviceName 服务名
     * @return 配置；不存在时 empty
     */
    public Optional<ServiceSlo> find(String serviceName) {
        String key = normalizeName(serviceName);
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }
        for (ServiceSlo slo : list()) {
            if (key.equalsIgnoreCase(slo.getServiceName())) {
                return Optional.of(slo);
            }
        }
        return Optional.empty();
    }

    /**
     * 创建一条 SLO；同名已存在则抛冲突。
     *
     * @param incoming 请求体
     * @return 规范化后的配置
     */
    public ServiceSlo create(ServiceSlo incoming) {
        ServiceSlo normalized = normalize(incoming);
        InsightRuntimeSettings merged = settingsService.snapshot();
        List<ServiceSlo> slos = copyAll(merged.getSlos());
        for (ServiceSlo existing : slos) {
            if (normalized.getServiceName().equalsIgnoreCase(existing.getServiceName())) {
                throw new IllegalStateException("SLO 已存在: " + normalized.getServiceName());
            }
        }
        slos.add(normalized);
        merged.setSlos(slos);
        settingsService.replaceSettings(merged);
        return copyOf(normalized);
    }

    /**
     * 更新已有 SLO。
     *
     * @param serviceName 路径中的服务名
     * @param incoming    请求体（可用 body.serviceName 覆盖显示名大小写）
     * @return 更新后配置
     */
    public ServiceSlo update(String serviceName, ServiceSlo incoming) {
        String key = normalizeName(serviceName);
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("serviceName 不能为空");
        }
        ServiceSlo patch = incoming != null ? incoming : new ServiceSlo();
        InsightRuntimeSettings merged = settingsService.snapshot();
        List<ServiceSlo> slos = copyAll(merged.getSlos());
        int idx = indexOf(slos, key);
        if (idx < 0) {
            throw new IllegalStateException("SLO 不存在: " + key);
        }
        ServiceSlo next = new ServiceSlo();
        next.setServiceName(key);
        if (StringUtils.hasText(patch.getServiceName())) {
            String renamed = normalizeName(patch.getServiceName());
            if (StringUtils.hasText(renamed) && !renamed.equalsIgnoreCase(key)) {
                if (indexOf(slos, renamed) >= 0) {
                    throw new IllegalStateException("SLO 已存在: " + renamed);
                }
                next.setServiceName(renamed);
            }
        }
        next.setTargetAvailability(patch.getTargetAvailability() > 0
                ? patch.getTargetAvailability()
                : slos.get(idx).getTargetAvailability());
        next.setWindowHours(patch.getWindowHours() > 0
                ? patch.getWindowHours()
                : slos.get(idx).getWindowHours());
        next = normalize(next);
        slos.set(idx, next);
        merged.setSlos(slos);
        settingsService.replaceSettings(merged);
        return copyOf(next);
    }

    /**
     * @param serviceName 服务名
     * @return 是否删除成功
     */
    public boolean delete(String serviceName) {
        String key = normalizeName(serviceName);
        if (!StringUtils.hasText(key)) {
            return false;
        }
        InsightRuntimeSettings merged = settingsService.snapshot();
        List<ServiceSlo> slos = copyAll(merged.getSlos());
        int idx = indexOf(slos, key);
        if (idx < 0) {
            return false;
        }
        slos.remove(idx);
        merged.setSlos(slos);
        settingsService.replaceSettings(merged);
        return true;
    }

    /**
     * 计算指定服务错误预算。
     *
     * @param serviceName 服务名
     * @param hours       统计窗口；{@code <=0} 时用 SLO 的 windowHours
     * @return 预算 Map；无 SLO 配置时 empty
     */
    public Optional<Map<String, Object>> budget(String serviceName, long hours) {
        Optional<ServiceSlo> sloOpt = find(serviceName);
        if (sloOpt.isEmpty()) {
            return Optional.empty();
        }
        ServiceSlo slo = sloOpt.get();
        long window = slo.getWindowHours() > 0 ? slo.getWindowHours() : ServiceSlo.DEFAULT_WINDOW_HOURS;
        long elapsed = hours > 0 ? hours : window;
        if (elapsed > window) {
            elapsed = window;
        }

        long since = System.currentTimeMillis() - elapsed * 3_600_000L;
        long total = 0L;
        long errors = 0L;
        for (Map<String, Object> row : persistenceService.getServiceLatencySummariesSince(since, Integer.MAX_VALUE)) {
            String name = String.valueOf(row.getOrDefault("service_name", ""));
            if (!slo.getServiceName().equalsIgnoreCase(name)) {
                continue;
            }
            total = asLong(row.get("span_count"));
            errors = asLong(row.get("error_count"));
            break;
        }

        return Optional.of(ErrorBudgetCalculator.compute(
                slo.getServiceName(),
                slo.getTargetAvailability(),
                window,
                elapsed,
                total,
                errors,
                System.currentTimeMillis()));
    }

    /**
     * 所有已配置 SLO 的预算快照（仪表盘用）。
     *
     * @param hours 统计窗口；{@code <=0} 表示各用自身 windowHours
     * @return 列表
     */
    public List<Map<String, Object>> listBudgets(long hours) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ServiceSlo slo : list()) {
            budget(slo.getServiceName(), hours > 0 ? hours : slo.getWindowHours())
                    .ifPresent(out::add);
        }
        return out;
    }

    /**
     * @param slo 原始
     * @return 规范化副本
     */
    public static ServiceSlo normalize(ServiceSlo slo) {
        ServiceSlo out = new ServiceSlo();
        if (slo == null) {
            throw new IllegalArgumentException("SLO 不能为空");
        }
        String name = normalizeName(slo.getServiceName());
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("serviceName 不能为空");
        }
        out.setServiceName(name);
        double target = slo.getTargetAvailability();
        if (Double.isNaN(target) || target <= 0 || target > 1.0) {
            throw new IllegalArgumentException("targetAvailability 须在 (0, 1] 内");
        }
        out.setTargetAvailability(target);
        long window = slo.getWindowHours() > 0 ? slo.getWindowHours() : ServiceSlo.DEFAULT_WINDOW_HOURS;
        if (window > 24L * 365L * 2L) {
            throw new IllegalArgumentException("windowHours 过大");
        }
        out.setWindowHours(window);
        return out;
    }

    /**
     * @param slo 配置
     * @return API 友好 Map
     */
    public static Map<String, Object> toMap(ServiceSlo slo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceName", slo.getServiceName());
        body.put("targetAvailability", slo.getTargetAvailability());
        body.put("windowHours", slo.getWindowHours());
        return body;
    }

    private static int indexOf(List<ServiceSlo> slos, String key) {
        for (int i = 0; i < slos.size(); i++) {
            if (key.equalsIgnoreCase(slos.get(i).getServiceName())) {
                return i;
            }
        }
        return -1;
    }

    private static List<ServiceSlo> copyAll(List<ServiceSlo> source) {
        List<ServiceSlo> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        for (ServiceSlo slo : source) {
            if (slo != null) {
                out.add(copyOf(slo));
            }
        }
        return out;
    }

    private static ServiceSlo copyOf(ServiceSlo slo) {
        ServiceSlo c = new ServiceSlo();
        c.setServiceName(slo.getServiceName());
        c.setTargetAvailability(slo.getTargetAvailability());
        c.setWindowHours(slo.getWindowHours());
        return c;
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private static long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }
}
