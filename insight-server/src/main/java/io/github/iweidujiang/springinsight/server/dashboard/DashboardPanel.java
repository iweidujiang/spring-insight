package io.github.iweidujiang.springinsight.server.dashboard;

import lombok.Data;

/**
 * 仪表盘单个面板的位置与可见性。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
@Data
public class DashboardPanel {

    /** 面板唯一 ID，如 kpi */
    private String id = "";

    /** 面板类型，如 kpi / period-summary / jdbc */
    private String type = "";

    /** 网格列（从 0 起） */
    private int x;

    /** 网格行（从 0 起） */
    private int y;

    /** 宽度（格） */
    private int w = 1;

    /** 高度（格） */
    private int h = 1;

    /** 是否显示 */
    private boolean visible = true;
}
