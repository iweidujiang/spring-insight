package io.github.iweidujiang.springinsight.agent.boot2.context;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

/**
 * CLIENT {@code remoteService} 规范化（Boot2）：优先逻辑服务名，避免拓扑边落成 IP。
 * <p>
 * 优先级：显式逻辑名 → 非 IP Host 头 → 非 IP URI host → URI host（含 IP）→ {@code unknown}。
 * </p>
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class RemoteServiceResolver {

    private RemoteServiceResolver() {
    }

    /**
     * @param uri             请求 URI，可为 null
     * @param hostHeader      Host 头，可为 null
     * @param explicitLogical 逻辑名，可为 null
     * @return remoteService，永不为 null
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
     * @param uri 请求 URI
     * @return remoteService
     */
    public static String resolve(URI uri) {
        return resolve(uri, null, null);
    }

    /**
     * @param url             URL
     * @param hostHeader      Host 头
     * @param explicitLogical 逻辑名
     * @return remoteService
     */
    public static String resolve(String url, String hostHeader, String explicitLogical) {
        URI uri = null;
        if (StringUtils.hasText(url)) {
            try {
                uri = URI.create(url.trim());
            } catch (Exception ignored) {
                // ignore
            }
        }
        return resolve(uri, hostHeader, explicitLogical);
    }

    /**
     * @param hostHeader 原始 Host
     * @return host 或 null
     */
    static String hostWithoutPort(String hostHeader) {
        String h = normalizeCandidate(hostHeader);
        if (h == null) {
            return null;
        }
        if (h.startsWith("[")) {
            int end = h.indexOf(']');
            if (end > 0) {
                return h.substring(0, end + 1);
            }
            return h;
        }
        int colon = h.indexOf(':');
        if (colon > 0 && h.indexOf(':', colon + 1) < 0) {
            return h.substring(0, colon);
        }
        return h;
    }

    /**
     * @param host 候选
     * @return 是否 IP / localhost
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
            return true;
        }
        String[] parts = h.split("\\.");
        if (parts.length == 4) {
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                if (p.isEmpty() || p.length() > 3) {
                    return false;
                }
                for (int j = 0; j < p.length(); j++) {
                    if (!Character.isDigit(p.charAt(j))) {
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
