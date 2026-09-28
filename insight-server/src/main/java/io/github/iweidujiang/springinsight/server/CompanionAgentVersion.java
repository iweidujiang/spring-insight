package io.github.iweidujiang.springinsight.server;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 解析本 Server 配套的 Agent / Starter 版本（与 {@code agent.version} 对齐，供关于页展示）。
 *
 * @since 2026-09-28
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class CompanionAgentVersion {

    /**
     * 工具类，禁止实例化。
     */
    private CompanionAgentVersion() {
    }

    /**
     * 优先配置 {@code spring.insight.agent.version}（构建时由 Maven 写入），否则 {@code unknown}。
     *
     * @param environment Spring 环境，可为 null
     * @return 非空版本字符串
     */
    public static String resolve(Environment environment) {
        if (environment != null) {
            String configured = environment.getProperty("spring.insight.agent.version");
            if (StringUtils.hasText(configured) && !configured.contains("@")) {
                return configured.trim();
            }
        }
        return "unknown";
    }
}
