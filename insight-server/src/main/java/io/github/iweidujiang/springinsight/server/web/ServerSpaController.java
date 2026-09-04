package io.github.iweidujiang.springinsight.server.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 控制台 SPA（Vue History 模式）回退控制器。
 * <p>
 * 静态资源由 Spring Boot 默认从 {@code classpath:/static/} 映射到根路径 {@code /}。
 * 浏览器直接访问前端路由（如 {@code /topology}）时，需转发到 {@code index.html}，
 * 再由 Vue Router 接管；API 路径（{@code /api/**}）不在此处理。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/8/13
 */
@Controller
public class ServerSpaController {

    /**
     * 将控制台前端路由统一转发到根目录的 {@code index.html}。
     * <p>
     * 不映射 {@code /} 本身：根路径由静态资源处理器直接返回 {@code index.html}，
     * 避免与静态映射冲突。
     * </p>
     *
     * @return Spring MVC forward 视图名，指向打包后的前端入口页
     */
    @GetMapping({
            "/dashboard",
            "/topology",
            "/traces",
            "/traces/**",
            "/error-analysis",
            "/about"
    })
    public String forwardSpaRoutesToIndex() {
        // forward 保留原始 URL，仅服务端内部转到 index.html，利于 History 刷新
        return "forward:/index.html";
    }
}
