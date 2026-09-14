package io.github.iweidujiang.springinsight.server.security;

import io.github.iweidujiang.springinsight.server.config.InsightServerSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 上报接口可选 Token 校验（默认关闭）。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public class IngestTokenAuthFilter extends OncePerRequestFilter {

    private final InsightServerSecurityProperties securityProperties;

    /**
     * @param securityProperties 安全配置
     */
    public IngestTokenAuthFilter(InsightServerSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /**
     * 仅拦截 {@code /api/v1/spans/*}；未开启鉴权时直接放行。
     *
     * @param request     请求
     * @param response    响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!securityProperties.isIngestAuthActive()) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/spans")) {
            filterChain.doFilter(request, response);
            return;
        }
        String provided = InsightTokenSupport.extractBearerOrInsightToken(request);
        if (!InsightTokenSupport.equalsConstantTime(securityProperties.normalizedIngestToken(), provided)) {
            writeUnauthorized(response, "invalid or missing ingest token");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 写入 401 JSON。
     *
     * @param response 响应
     * @param message  原因
     */
    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }
}
