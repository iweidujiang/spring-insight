package io.github.iweidujiang.springinsight.agent.boot2;

import java.io.InputStream;
import java.util.Properties;

/**
 * Agent 构建元信息：版本号与上报协议号（写入各批次 / 心跳）。
 *
 * @author 苏渡苇
 * @since 2026-11-23
 */
public final class AgentBuildInfo {

    /** 当前上报协议号（与 Server 约定；无破坏性字段扩展时保持不变） */
    public static final String PROTOCOL = "1";

    private static final String VERSION = loadVersion();

    private AgentBuildInfo() {
    }

    /**
     * @return 非空 Agent 版本（可能为 {@code unknown}）
     */
    public static String agentVersion() {
        return VERSION;
    }

    /**
     * @return 上报协议号
     */
    public static String protocol() {
        return PROTOCOL;
    }

    private static String loadVersion() {
        try {
            InputStream in = AgentBuildInfo.class.getClassLoader()
                    .getResourceAsStream("META-INF/spring-insight-agent.properties");
            if (in != null) {
                try {
                    Properties props = new Properties();
                    props.load(in);
                    String v = trim(props.getProperty("agent.version"));
                    if (!v.isEmpty() && v.indexOf("${") < 0 && v.indexOf("@") < 0) {
                        return v;
                    }
                } finally {
                    in.close();
                }
            }
        } catch (Exception ignored) {
            // 回退到包清单 / 未知
        }
        Package pkg = AgentBuildInfo.class.getPackage();
        if (pkg != null) {
            String impl = trim(pkg.getImplementationVersion());
            if (!impl.isEmpty()) {
                return impl;
            }
        }
        return "unknown";
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
