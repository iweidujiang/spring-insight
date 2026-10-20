package io.github.iweidujiang.springinsight.server.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认布局与预设模板工厂。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
public final class DashboardLayouts {

    /** 默认模板 ID */
    public static final String TEMPLATE_OVERVIEW = "overview";

    public static final String TEMPLATE_TROUBLESHOOT = "troubleshoot";
    public static final String TEMPLATE_JDBC = "jdbc";
    public static final String TEMPLATE_REDIS = "redis";
    public static final String TEMPLATE_MINIMAL = "minimal";

    private DashboardLayouts() {
    }

    /**
     * @return 全局概览（默认）布局副本
     */
    public static DashboardLayout defaultOverview() {
        return overview();
    }

    /**
     * @return 全部预设模板（含 layout 副本）
     */
    public static List<Map<String, Object>> templates() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(template(TEMPLATE_OVERVIEW, "全局概览", "KPI、时段摘要、库缓存与服务总览（默认）", overview()));
        list.add(template(TEMPLATE_TROUBLESHOOT, "排障聚焦", "突出错误、慢服务与最近请求，便于排障", troubleshoot()));
        list.add(template(TEMPLATE_JDBC, "数据库专项", "聚焦 JDBC 指标与慢链路", jdbc()));
        list.add(template(TEMPLATE_REDIS, "缓存专项", "聚焦 Redis 指标与慢命令", redis()));
        list.add(template(TEMPLATE_MINIMAL, "极简", "仅保留 KPI 与时段摘要", minimal()));
        return list;
    }

    /**
     * 按模板 ID 取布局；未知 ID 时返回默认概览。
     *
     * @param templateId 模板 ID
     * @return 布局副本
     */
    public static DashboardLayout byTemplateId(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return overview();
        }
        return switch (templateId.trim()) {
            case TEMPLATE_TROUBLESHOOT -> troubleshoot();
            case TEMPLATE_JDBC -> jdbc();
            case TEMPLATE_REDIS -> redis();
            case TEMPLATE_MINIMAL -> minimal();
            default -> overview();
        };
    }

    private static Map<String, Object> template(String id, String name, String description, DashboardLayout layout) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("description", description);
        m.put("layout", copy(layout));
        return m;
    }

    /** 全局概览：与当前仪表盘区块对齐的默认网格（宽 4 列） */
    static DashboardLayout overview() {
        DashboardLayout layout = new DashboardLayout();
        layout.setTemplateId(TEMPLATE_OVERVIEW);
        layout.setPanels(List.of(
                panel("kpi", "kpi", 0, 0, 4, 1, true),
                panel("summary", "period-summary", 0, 1, 4, 1, true),
                panel("collector", "collector", 0, 2, 4, 1, true),
                panel("jdbc", "jdbc", 0, 3, 2, 1, true),
                panel("redis", "redis", 2, 3, 2, 1, true),
                panel("service-health", "service-health", 0, 4, 2, 2, true),
                panel("slow-services", "slow-services", 2, 4, 1, 1, true),
                panel("hot-dependencies", "hot-dependencies", 3, 4, 1, 1, true),
                panel("recent-traces", "recent-traces", 0, 6, 2, 2, true),
                panel("service-rank", "service-rank", 2, 6, 2, 2, true)
        ));
        return layout;
    }

    static DashboardLayout troubleshoot() {
        DashboardLayout layout = new DashboardLayout();
        layout.setTemplateId(TEMPLATE_TROUBLESHOOT);
        layout.setPanels(List.of(
                panel("kpi", "kpi", 0, 0, 4, 1, true),
                panel("summary", "period-summary", 0, 1, 4, 1, true),
                panel("error-services", "error-services", 0, 2, 2, 2, true),
                panel("slow-services", "slow-services", 2, 2, 2, 2, true),
                panel("recent-traces", "recent-traces", 0, 4, 4, 2, true),
                panel("collector", "collector", 0, 6, 4, 1, true),
                panel("jdbc", "jdbc", 0, 7, 2, 1, false),
                panel("redis", "redis", 2, 7, 2, 1, false),
                panel("service-health", "service-health", 0, 8, 2, 1, false),
                panel("hot-dependencies", "hot-dependencies", 2, 8, 2, 1, false),
                panel("service-rank", "service-rank", 0, 9, 4, 1, false)
        ));
        return layout;
    }

    static DashboardLayout jdbc() {
        DashboardLayout layout = new DashboardLayout();
        layout.setTemplateId(TEMPLATE_JDBC);
        layout.setPanels(List.of(
                panel("kpi", "kpi", 0, 0, 4, 1, true),
                panel("jdbc", "jdbc", 0, 1, 4, 2, true),
                panel("slow-services", "slow-services", 0, 3, 2, 2, true),
                panel("recent-traces", "recent-traces", 2, 3, 2, 2, true),
                panel("summary", "period-summary", 0, 5, 4, 1, true),
                panel("collector", "collector", 0, 6, 4, 1, false),
                panel("redis", "redis", 0, 7, 2, 1, false),
                panel("service-health", "service-health", 2, 7, 2, 1, false),
                panel("hot-dependencies", "hot-dependencies", 0, 8, 2, 1, false),
                panel("service-rank", "service-rank", 2, 8, 2, 1, false),
                panel("error-services", "error-services", 0, 9, 4, 1, false)
        ));
        return layout;
    }

    static DashboardLayout redis() {
        DashboardLayout layout = new DashboardLayout();
        layout.setTemplateId(TEMPLATE_REDIS);
        layout.setPanels(List.of(
                panel("kpi", "kpi", 0, 0, 4, 1, true),
                panel("redis", "redis", 0, 1, 4, 2, true),
                panel("slow-services", "slow-services", 0, 3, 2, 2, true),
                panel("recent-traces", "recent-traces", 2, 3, 2, 2, true),
                panel("summary", "period-summary", 0, 5, 4, 1, true),
                panel("collector", "collector", 0, 6, 4, 1, false),
                panel("jdbc", "jdbc", 0, 7, 2, 1, false),
                panel("service-health", "service-health", 2, 7, 2, 1, false),
                panel("hot-dependencies", "hot-dependencies", 0, 8, 2, 1, false),
                panel("service-rank", "service-rank", 2, 8, 2, 1, false),
                panel("error-services", "error-services", 0, 9, 4, 1, false)
        ));
        return layout;
    }

    static DashboardLayout minimal() {
        DashboardLayout layout = new DashboardLayout();
        layout.setTemplateId(TEMPLATE_MINIMAL);
        layout.setPanels(List.of(
                panel("kpi", "kpi", 0, 0, 4, 1, true),
                panel("summary", "period-summary", 0, 1, 4, 1, true),
                panel("collector", "collector", 0, 2, 4, 1, false),
                panel("jdbc", "jdbc", 0, 3, 2, 1, false),
                panel("redis", "redis", 2, 3, 2, 1, false),
                panel("service-health", "service-health", 0, 4, 2, 1, false),
                panel("slow-services", "slow-services", 2, 4, 1, 1, false),
                panel("hot-dependencies", "hot-dependencies", 3, 4, 1, 1, false),
                panel("recent-traces", "recent-traces", 0, 5, 2, 1, false),
                panel("service-rank", "service-rank", 2, 5, 2, 1, false)
        ));
        return layout;
    }

    /**
     * 深拷贝布局。
     *
     * @param src 源；可为 null
     * @return 副本；源为空时返回空布局
     */
    public static DashboardLayout copy(DashboardLayout src) {
        DashboardLayout out = new DashboardLayout();
        if (src == null) {
            return out;
        }
        out.setTemplateId(src.getTemplateId());
        List<DashboardPanel> panels = new ArrayList<>();
        if (src.getPanels() != null) {
            for (DashboardPanel p : src.getPanels()) {
                panels.add(copyPanel(p));
            }
        }
        out.setPanels(panels);
        return out;
    }

    private static DashboardPanel copyPanel(DashboardPanel src) {
        DashboardPanel p = new DashboardPanel();
        if (src == null) {
            return p;
        }
        p.setId(src.getId() == null ? "" : src.getId());
        p.setType(src.getType() == null ? "" : src.getType());
        p.setX(src.getX());
        p.setY(src.getY());
        p.setW(src.getW());
        p.setH(src.getH());
        p.setVisible(src.isVisible());
        return p;
    }

    private static DashboardPanel panel(String id, String type, int x, int y, int w, int h, boolean visible) {
        DashboardPanel p = new DashboardPanel();
        p.setId(id);
        p.setType(type);
        p.setX(x);
        p.setY(y);
        p.setW(w);
        p.setH(h);
        p.setVisible(visible);
        return p;
    }
}
