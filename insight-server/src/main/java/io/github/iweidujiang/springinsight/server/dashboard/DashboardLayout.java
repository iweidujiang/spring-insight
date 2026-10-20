package io.github.iweidujiang.springinsight.server.dashboard;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 仪表盘布局（面板列表）。
 *
 * @author 苏渡苇
 * @since 2026-10-20
 */
@Data
public class DashboardLayout {

    /** 可选：所用模板 ID（如 overview） */
    private String templateId;

    /** 面板列表 */
    private List<DashboardPanel> panels = new ArrayList<>();
}
