package io.github.iweidujiang.springinsight.controller;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 控制台 SPA：History 路由回退到 index.html。路径前缀由 {@code spring.insight.ui-base-path} 决定，空则挂在根路径。
 */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class SpaController {

    private final InsightProperties insightProperties;

    /**
     * 使用 {@link ResponseEntity} 302，避免依赖 {@code RedirectView}（spring-webmvc），否则在仅 WebFlux 的 classpath 上扫描到本类即可能触发接口解析失败。
     */
    @GetMapping("/insight-ui")
    public ResponseEntity<Void> redirectLegacyInsightUi() {
        String base = insightProperties.normalizeUiBasePath();
        String location = base.isEmpty() ? "/" : base + "/";
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
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
