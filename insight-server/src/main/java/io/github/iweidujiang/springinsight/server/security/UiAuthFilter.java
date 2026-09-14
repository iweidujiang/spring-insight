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
 * 控制台 UI API 可选登录校验（默认关闭；静态资源与 /api/v1/auth/** 放行）。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public class UiAuthFilter extends OncePerRequestFilter {

    private final InsightServerSecurityProperties securityProperties;
    private final UiSessionStore sessionStore;

    /**
     * @param securityProperties 安全配置
     * @param sessionStore       会话表
     */
    public UiAuthFilter(InsightServerSecurityProperties securityProperties, UiSessionStore sessionStore) {
        this.securityProperties = securityProperties;
        this.sessionStore = sessionStore;
    }

    /**
     * 仅保护 {@code /api/v1/ui/**}。
     *
     * @param request     请求
     * @param response    响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!securityProperties.isUiAuthEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/ui")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = InsightTokenSupport.extractUiToken(request);
        if (!sessionStore.isValid(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"ui authentication required\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
