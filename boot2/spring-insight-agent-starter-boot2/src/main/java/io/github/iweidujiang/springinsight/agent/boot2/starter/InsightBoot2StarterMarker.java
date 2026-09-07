/*
 * Copyright (c) 2026, 苏渡苇. All rights reserved.
 *
 * spring-insight-agent-starter-boot2：Boot2 业务侧唯一推荐依赖（传递 insight-agent-boot2）。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.starter;

/**
 * Starter 标记类：业务侧只需依赖本模块坐标，无需依赖 {@code insight-agent-boot2}。
 * <p>
 * 自动配置由传递依赖中的 {@code META-INF/spring.factories} 加载
 * （{@code InsightBoot2AutoConfiguration}、{@code InsightBoot2FeignAutoConfiguration}）。
 * </p>
 */
public final class InsightBoot2StarterMarker {

    /**
     * 禁止实例化。
     */
    private InsightBoot2StarterMarker() {
    }
}
