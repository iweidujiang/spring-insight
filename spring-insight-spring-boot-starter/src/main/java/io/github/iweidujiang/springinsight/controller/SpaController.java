package io.github.iweidujiang.springinsight.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 SPA路由转发控制器
 * │ 用于处理单页应用的路由请求，将所有前端路由转发到index.html
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │ 📅 @since 2026/1/21
 * └───────────────────────────────────────────────
 */
@Controller
public class SpaController {

    /**
     * 兼容文档中的 /insight-ui 入口，重定向到 SPA 根路径
     */
    @GetMapping("/insight-ui")
    public RedirectView redirectLegacyInsightUi() {
        return new RedirectView("/", true, false);
    }

    /**
     * 处理SPA路由转发
     * 将所有前端路由请求转发到index.html
     */
    @GetMapping(value = {
            "/",
            "/dashboard",
            "/topology",
            "/traces",
            "/traces/**",
            "/error-analysis",
            "/about"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}