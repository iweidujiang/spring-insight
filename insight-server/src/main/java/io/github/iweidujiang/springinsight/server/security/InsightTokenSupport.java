package io.github.iweidujiang.springinsight.server.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 从请求解析 Insight Token，并做常量时间比较。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class InsightTokenSupport {

    /** Agent / Server 约定的上报 Header */
    public static final String HEADER_INSIGHT_TOKEN = "X-Insight-Token";

    /** UI 会话 Header（与 Bearer 二选一） */
    public static final String HEADER_UI_TOKEN = "X-Insight-Ui-Token";

    private InsightTokenSupport() {
    }

    /**
     * 优先读 {@code X-Insight-Token}，否则解析 {@code Authorization: Bearer}。
     *
     * @param request HTTP 请求
     * @return token；缺失时为空串
     */
    public static String extractBearerOrInsightToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_INSIGHT_TOKEN);
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        return extractBearer(request);
    }

    /**
     * 优先读 {@code X-Insight-Ui-Token}，否则 Bearer。
     *
     * @param request HTTP 请求
     * @return token；缺失时为空串
     */
    public static String extractUiToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_UI_TOKEN);
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        return extractBearer(request);
    }

    /**
     * @param request HTTP 请求
     * @return Bearer 凭证；无则空串
     */
    public static String extractBearer(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (!StringUtils.hasText(auth)) {
            return "";
        }
        String t = auth.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return t.substring(7).trim();
        }
        return "";
    }

    /**
     * 常量时间比较，降低时序侧信道风险。
     *
     * @param expected 期望值
     * @param actual   实际值
     * @return 是否相等
     */
    public static boolean equalsConstantTime(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
