package io.github.iweidujiang.springinsight.agent.context;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

/**
 * CLIENT {@code remoteService} 规范化：优先逻辑服务名，避免拓扑边落成 {@code 127.0.0.1} 等实例地址。
 * <p>
 * 优先级：显式逻辑名（如 {@code lb://} host / Feign Target 名）→ 非 IP 的 Host 头 → 非 IP 的 URI host →
 * URI host（含 IP，直连兜底）→ {@code unknown}。
 * </p>
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class RemoteServiceResolver {

    private RemoteServiceResolver() {
    }

    /**
     * 解析远程服务标识。
     *
     * @param uri            请求 URI，可为 null
     * @param hostHeader     HTTP {@code Host} 头（可含端口），可为 null
     * @param explicitLogical 已知逻辑名（lb 服务名、Feign name 等），可为 null
     * @return 规范化后的 remoteService，永不为 null
     */
    public static String resolve(URI uri, String hostHeader, String explicitLogical) {
        String logical = normalizeCandidate(explicitLogical);
        if (logical != null && !isIpOrLocalhost(logical)) {
            return logical;
        }

        String fromHeader = hostWithoutPort(hostHeader);
        if (fromHeader != null && !isIpOrLocalhost(fromHeader)) {
            return fromHeader;
        }

        String fromUri = uri != null ? normalizeCandidate(uri.getHost()) : null;
        if (fromUri != null && !isIpOrLocalhost(fromUri)) {
            return fromUri;
        }

        if (fromUri != null) {
            return fromUri;
        }
        if (fromHeader != null) {
            return fromHeader;
        }
        return "unknown";
    }

    /**
     * 仅从 URI 解析（无 Host / 逻辑名时）。
     *
     * @param uri 请求 URI
     * @return remoteService
     */
    public static String resolve(URI uri) {
        return resolve(uri, null, null);
    }

    /**
     * 从 URL 字符串解析。
     *
     * @param url            URL
     * @param hostHeader     Host 头
     * @param explicitLogical 逻辑名
     * @return remoteService
     */
    public static String resolve(String url, String hostHeader, String explicitLogical) {
        URI uri = null;
        if (StringUtils.hasText(url)) {
            try {
                uri = URI.create(url.trim());
            } catch (Exception ignored) {
                // 非法 URL：仍尝试 Host / 逻辑名
            }
        }
        return resolve(uri, hostHeader, explicitLogical);
    }

    /**
     * 去掉 Host 头端口与空白。
     *
     * @param hostHeader 原始 Host
     * @return host 或 null
     */
    static String hostWithoutPort(String hostHeader) {
        String h = normalizeCandidate(hostHeader);
        if (h == null) {
            return null;
        }
        // IPv6 字面量 [::1]:port
        if (h.startsWith("[")) {
            int end = h.indexOf(']');
            if (end > 0) {
                return h.substring(0, end + 1);
            }
            return h;
        }
        int colon = h.indexOf(':');
        if (colon > 0 && h.indexOf(':', colon + 1) < 0) {
            // 单个冒号视为 host:port
            return h.substring(0, colon);
        }
        return h;
    }

    /**
     * @param host 候选 host
     * @return 是否为 IP 字面量或 localhost
     */
    static boolean isIpOrLocalhost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        if ("localhost".equals(h) || "0.0.0.0".equals(h)) {
            return true;
        }
        if (h.startsWith("[") && h.endsWith("]")) {
            return true; // IPv6 字面量
        }
        // 粗判 IPv4：四段数字
        String[] parts = h.split("\\.");
        if (parts.length == 4) {
            for (String p : parts) {
                if (p.isEmpty() || p.length() > 3) {
                    return false;
                }
                for (int i = 0; i < p.length(); i++) {
                    if (!Character.isDigit(p.charAt(i))) {
                        return false;
                    }
                }
                int v = Integer.parseInt(p);
                if (v < 0 || v > 255) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static String normalizeCandidate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}
