package io.github.iweidujiang.springinsight.ui.controller;

import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import io.github.iweidujiang.springinsight.ui.service.ApiService;
import io.github.iweidujiang.springinsight.ui.service.DataCollectorService;
import io.github.iweidujiang.springinsight.ui.service.MockDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 主控制器 - 处理页面请求
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │ 📅 @since 2026/1/12
 * └───────────────────────────────────────────────
 */
@Slf4j
@Controller
public class MainController {

    private final DataCollectorService dataCollectorService;

    public MainController(DataCollectorService dataCollectorService) {
        this.dataCollectorService = dataCollectorService;
    }

    /**
     * 首页 - 仪表盘
     */
    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        try {
            // 获取服务列表
            List<String> services = dataCollectorService.getServiceNames();
            model.addAttribute("services", services);

            // 获取服务依赖关系（最近24小时）
            var dependencies = dataCollectorService.getServiceDependencies(24);
            model.addAttribute("dependencies", dependencies);

            // 获取各服务Span数量统计
            var serviceStats = dataCollectorService.getServiceStats();
            model.addAttribute("serviceStats", serviceStats);
            model.addAttribute("spanCounts", serviceStats);

            // 获取高错误率服务
            var errorAnalysis = dataCollectorService.getErrorAnalysis(24);
            model.addAttribute("errorAnalysis", errorAnalysis);
            model.addAttribute("errorServices", errorAnalysis);

            // 获取collector统计
            var collectorStats = dataCollectorService.getCollectorStats();
            model.addAttribute("collectorStats", collectorStats);

            log.info("仪表盘数据加载完成，服务数: {}", services.size());
            return "dashboard";

        } catch (Exception e) {
            log.error("加载仪表盘数据失败", e);
            model.addAttribute("error", "加载数据失败: " + e.getMessage());
            return "dashboard";
        }
    }

    /**
     * 服务拓扑图页面
     */
    @GetMapping("/topology")
    public String topology(Model model) {
        try {
            var dependencies = dataCollectorService.getServiceDependencies(24);
            model.addAttribute("dependencies", dependencies);
            return "topology";
        } catch (Exception e) {
            log.error("加载拓扑图数据失败", e);
            model.addAttribute("error", "加载拓扑图数据失败: " + e.getMessage());
            return "topology";
        }
    }

    @GetMapping("/traces")
    public String traces(
            @RequestParam(value = "service", required = false) String serviceName,
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            Model model) {

        try {
            List<String> services = dataCollectorService.getServiceNames();
            model.addAttribute("services", services);
            model.addAttribute("selectedService", serviceName);
            model.addAttribute("selectedHours", hours);
            model.addAttribute("selectedLimit", limit);

            // 获取链路追踪数据
            List<?> traces;
            if (serviceName != null && !serviceName.isEmpty()) {
                traces = dataCollectorService.getRecentSpansByService(serviceName, limit);
            } else {
                traces = dataCollectorService.getRecentSpans(hours, limit);
            }

            model.addAttribute("traces", traces);
            return "traces";

        } catch (Exception e) {
            log.error("加载链路追踪数据失败", e);
            model.addAttribute("error", "加载链路追踪数据失败: " + e.getMessage());
            return "traces";
        }
    }

    @GetMapping("/trace")
    public String traceDetail(
            @RequestParam("id") String traceId,
            Model model) {

        try {
            var traceSpans = dataCollectorService.getTraceDetail(traceId);
            model.addAttribute("traceId", traceId);
            model.addAttribute("traceSpans", traceSpans);

            if (!traceSpans.isEmpty()) {
                // 计算总体统计信息
                long totalDuration = traceSpans.stream()
                        .filter(span -> span.getDurationMs() != null)
                        .mapToLong(span -> span.getDurationMs())
                        .sum();
                model.addAttribute("totalDuration", totalDuration);
                model.addAttribute("spanCount", traceSpans.size());

                // 查找根Span
                var rootSpan = traceSpans.stream()
                        .filter(span -> span.getParentSpanId() == null || span.getParentSpanId().isEmpty())
                        .findFirst();
                rootSpan.ifPresent(span -> model.addAttribute("rootOperation", span.getOperationName()));
            }

            return "trace-detail";

        } catch (Exception e) {
            log.error("加载链路详情失败", e);
            model.addAttribute("error", "加载链路详情失败: " + e.getMessage());
            return "trace-detail";
        }
    }

    @GetMapping("/error-analysis")
    public String errorAnalysis(
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            Model model) {

        try {
            var errorAnalysis = dataCollectorService.getErrorAnalysis(hours);
            model.addAttribute("errorAnalysis", errorAnalysis);
            model.addAttribute("hours", hours);
            return "error-analysis";

        } catch (Exception e) {
            log.error("加载错误分析数据失败", e);
            model.addAttribute("error", "加载错误分析数据失败: " + e.getMessage());
            return "error-analysis";
        }
    }

    @GetMapping("/about")
    public String about(Model model) {
        try {
            var collectorStats = dataCollectorService.getCollectorStats();
            model.addAttribute("collectorStats", collectorStats);
            model.addAttribute("cacheSize", dataCollectorService.getCacheSize());
            return "about";
        } catch (Exception e) {
            log.error("加载关于页面数据失败", e);
            return "about";
        }
    }
}
