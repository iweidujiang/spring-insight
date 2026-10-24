package io.github.iweidujiang.springinsight.server.slo;

import lombok.Data;

/**
 * 单服务 SLO 目标（可用性）。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
@Data
public class ServiceSlo {

    /** 默认观察窗口：30 天。 */
    public static final long DEFAULT_WINDOW_HOURS = 720L;

    /** 服务名（唯一键）。 */
    private String serviceName = "";

    /** 目标可用性，如 {@code 0.999} 表示 99.9%。 */
    private double targetAvailability = 0.999;

    /** 错误预算窗口小时数。 */
    private long windowHours = DEFAULT_WINDOW_HOURS;
}
