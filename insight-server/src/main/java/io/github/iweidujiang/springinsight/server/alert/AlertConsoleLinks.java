package io.github.iweidujiang.springinsight.server.alert;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警控制台深链：相对路径与绝对 URL（错误链路 / 服务详情）。
 *
 * @author 苏渡苇
 * @since 2026-11-17
 */
public final class AlertConsoleLinks {

    private AlertConsoleLinks() {
    }

    /**
     * 由告警窗口分钟数换算控制台 {@code hours}（至少 1）。
     *
     * @param windowMinutes 告警窗
     * @return 小时数
     */
    public static int hoursFromWindow(int windowMinutes) {
        if (windowMinutes <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(windowMinutes / 60.0));
    }

    /**
     * 是否错误类指标（主链指向错误链路列表）。
     *
     * @param metric 指标名
     * @return true 表示错误类
     */
    public static boolean isErrorMetric(String metric) {
        String m = metric == null ? "" : metric.trim().toLowerCase();
        return "error_rate".equals(m) || "error_count".equals(m);
    }

    /**
     * 构建相对路径与可选绝对 URL。
     *
     * @param consoleBaseUrl 控制台根地址；可空则只写相对路径
     * @param serviceName    服务名
     * @param metric         指标
     * @param windowMinutes  告警窗分钟
     * @return consoleLinks Map
     */
    public static Map<String, Object> build(String consoleBaseUrl,
                                            String serviceName,
                                            String metric,
                                            int windowMinutes) {
        String svc = serviceName == null ? "" : serviceName.trim();
        int hours = hoursFromWindow(windowMinutes);
        String encoded = URLEncoder.encode(svc.isEmpty() ? "unknown" : svc, StandardCharsets.UTF_8);
        String tracesPath = "/traces?service=" + encoded + "&status=error&hours=" + hours;
        String servicePath = "/services/" + encoded + "?hours=" + hours;
        String primaryPath = isErrorMetric(metric) ? tracesPath : servicePath;

        Map<String, Object> links = new LinkedHashMap<>();
        links.put("hours", hours);
        links.put("tracesPath", tracesPath);
        links.put("servicePath", servicePath);
        links.put("primaryPath", primaryPath);

        String base = normalizeBase(consoleBaseUrl);
        if (!base.isEmpty()) {
            links.put("tracesUrl", base + tracesPath);
            links.put("serviceUrl", base + servicePath);
            links.put("primaryUrl", base + primaryPath);
        }
        return links;
    }

    /**
     * 相对路径拼绝对 URL。
     *
     * @param consoleBaseUrl 根地址
     * @param path           以 / 开头的路径与 query
     * @return 绝对 URL；根地址为空时返回 path
     */
    public static String absolute(String consoleBaseUrl, String path) {
        String p = path == null ? "" : path.trim();
        if (p.isEmpty()) {
            return "";
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        String base = normalizeBase(consoleBaseUrl);
        return base.isEmpty() ? p : base + p;
    }

    /**
     * 从 payload 取主按钮 URL：优先用 {@code consoleLinks.primaryPath} + 本渠道根地址。
     *
     * @param fields         告警字段
     * @param consoleBaseUrl 渠道控制台根地址
     * @return 绝对或相对主链
     */
    public static String primaryUrl(Map<String, Object> fields, String consoleBaseUrl) {
        String base = normalizeBase(consoleBaseUrl);
        String path = null;
        if (fields != null) {
            Object linksObj = fields.get("consoleLinks");
            if (linksObj instanceof Map<?, ?> links) {
                Object p = links.get("primaryPath");
                if (p != null && !String.valueOf(p).isBlank()) {
                    path = String.valueOf(p).trim();
                }
            }
            if (path == null) {
                Object p = fields.get("tracesPath");
                String metric = text(fields, "metric", "");
                if (isErrorMetric(metric) && p != null && !String.valueOf(p).isBlank()) {
                    path = String.valueOf(p).trim();
                } else {
                    Object sp = fields.get("servicePath");
                    if (sp != null && !String.valueOf(sp).isBlank()) {
                        path = String.valueOf(sp).trim();
                    }
                }
            }
        }
        if (path == null || path.isBlank()) {
            String service = text(fields, "serviceName", "unknown");
            String metric = text(fields, "metric", "");
            int window = intVal(fields, "windowMinutes", 15);
            path = String.valueOf(build("", service, metric, window).get("primaryPath"));
        }
        if (!base.isEmpty()) {
            return absolute(base, path);
        }
        if (fields != null) {
            Object flat = fields.get("primaryUrl");
            if (flat != null && !String.valueOf(flat).isBlank()) {
                return String.valueOf(flat).trim();
            }
        }
        return path;
    }

    /**
     * 去掉尾部斜杠。
     *
     * @param consoleBaseUrl 根地址
     * @return 规范化根
     */
    public static String normalizeBase(String consoleBaseUrl) {
        String base = consoleBaseUrl == null ? "" : consoleBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String text(Map<String, Object> fields, String key, String fallback) {
        if (fields == null || !fields.containsKey(key) || fields.get(key) == null) {
            return fallback;
        }
        String v = String.valueOf(fields.get(key)).trim();
        return v.isEmpty() ? fallback : v;
    }

    private static int intVal(Map<String, Object> fields, String key, int fallback) {
        if (fields == null || !(fields.get(key) instanceof Number n)) {
            return fallback;
        }
        return n.intValue();
    }
}
