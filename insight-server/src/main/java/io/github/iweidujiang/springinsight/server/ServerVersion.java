package io.github.iweidujiang.springinsight.server;

import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 解析 insight-server 对外展示的版本号（配置 / build-info / Manifest）。
 *
 * @since 2026-09-17
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class ServerVersion {

    private ServerVersion() {
    }

    /**
     * 优先配置 {@code spring.insight.server.version}，其次 {@link BuildProperties}，
     * 再次 jar Manifest Implementation-Version，否则 {@code dev}。
     *
     * @param environment     Spring 环境，可为 null
     * @param buildProperties Maven build-info，可为 null（未执行 build-info 时）
     * @return 非空版本字符串
     */
    public static String resolve(Environment environment, BuildProperties buildProperties) {
        if (environment != null) {
            String configured = environment.getProperty("spring.insight.server.version");
            if (StringUtils.hasText(configured)) {
                return configured.trim();
            }
        }
        if (buildProperties != null && StringUtils.hasText(buildProperties.getVersion())) {
            return buildProperties.getVersion().trim();
        }
        Package pkg = InsightServerApplication.class.getPackage();
        if (pkg != null && StringUtils.hasText(pkg.getImplementationVersion())) {
            return pkg.getImplementationVersion().trim();
        }
        return "dev";
    }

    /**
     * 无 BuildProperties 时的便捷重载。
     *
     * @param environment Spring 环境
     * @return 版本字符串
     */
    public static String resolve(Environment environment) {
        return resolve(environment, null);
    }
}
