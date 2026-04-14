package io.github.iweidujiang.springinsight.controller;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 控制台 SPA：History 路由回退到 index.html。路径前缀由 {@code spring.insight.ui-base-path} 决定，空则挂在根路径。
 */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class SpaController {

    private final InsightProperties insightProperties;

    @GetMapping("/insight-ui")
    public RedirectView redirectLegacyInsightUi() {
        String base = insightProperties.normalizeUiBasePath();
        if (base.isEmpty()) {
            return new RedirectView("/", true, false);
        }
        return new RedirectView(base + "/", true, false);
    }

    @GetMapping({
            "${spring.insight.ui-base-path:/spring-insight}/",
            "${spring.insight.ui-base-path:/spring-insight}/dashboard",
            "${spring.insight.ui-base-path:/spring-insight}/topology",
            "${spring.insight.ui-base-path:/spring-insight}/traces",
            "${spring.insight.ui-base-path:/spring-insight}/traces/**",
            "${spring.insight.ui-base-path:/spring-insight}/error-analysis",
            "${spring.insight.ui-base-path:/spring-insight}/about"
    })
    public String forwardToIndex() {
        String base = insightProperties.normalizeUiBasePath();
        if (base.isEmpty()) {
            return "forward:/index.html";
        }
        return "forward:" + base + "/index.html";
    }
}
