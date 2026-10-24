package io.github.iweidujiang.springinsight.server.alert;

import lombok.Data;

/**
 * 细粒度告警静默规则（按服务 / 指标 / 时段）。
 *
 * @author 苏渡苇
 * @since 2026-10-24
 */
@Data
public class SilenceRule {

    /** 规则 id（创建时生成）。 */
    private String id = "";

    /** 服务名；空表示匹配所有服务。 */
    private String serviceName = "";

    /** 指标名；空表示匹配所有指标。 */
    private String metric = "";

    /** 静默开始（本地时区 HH:mm）。 */
    private String startTime = "00:00";

    /** 静默结束（本地时区 HH:mm）；可跨午夜。 */
    private String endTime = "00:00";

    /** 时区；空则 JVM 默认。 */
    private String timezone = "";

    /** 静默原因（展示用）。 */
    private String reason = "";
}
