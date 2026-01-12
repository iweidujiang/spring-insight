package io.github.iweidujiang.springinsight.ui.ui;

import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    private final TraceSpanPersistenceService traceSpanPersistenceService;

    public MainController(TraceSpanPersistenceService traceSpanPersistenceService) {
        this.traceSpanPersistenceService = traceSpanPersistenceService;
    }

    /**
     * 首页 - 仪表盘
     */
    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        try {
            // 获取服务列表
            List<String> services = traceSpanPersistenceService.getAllServiceNames();
            model.addAttribute("services", services);

            // 获取服务依赖关系（最近24小时）
            List<Map<String, Object>> dependencies = traceSpanPersistenceService.getServiceDependencies(24);
            model.addAttribute("dependencies", dependencies);

            // 获取各服务Span数量统计
            List<Map<String, Object>> spanCounts = traceSpanPersistenceService.getSpanCountByService();
            model.addAttribute("spanCounts", spanCounts);

            // 获取高错误率服务
            List<Map<String, Object>> errorServices = traceSpanPersistenceService.findHighErrorServices(24);
            model.addAttribute("errorServices", errorServices);

            log.debug("仪表盘数据加载完成，服务数: {}", services.size());
            return "dashboard";
        } catch (Exception e) {
            log.error("加载仪表盘数据失败", e);
            model.addAttribute("error", "加载数据失败: " + e.getMessage());
            return "error";
        }
    }

    /**
     * 服务拓扑图页面
     */
    @GetMapping("/topology")
    public String topology(Model model) {
        try {
            List<Map<String, Object>> dependencies = traceSpanPersistenceService.getServiceDependencies(24);
            model.addAttribute("dependencies", dependencies);
            return "topology";
        } catch (Exception e) {
            log.error("加载拓扑图数据失败", e);
            model.addAttribute("error", "加载拓扑图数据失败: " + e.getMessage());
            return "error";
        }
    }

    /**
     * 链路追踪列表页面
     */
    @GetMapping("/traces")
    public String traces(
            @RequestParam(value = "service", required = false) String serviceName,
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            Model model) {

        try {
            List<String> services = traceSpanPersistenceService.getAllServiceNames();
            model.addAttribute("services", services);
            model.addAttribute("selectedService", serviceName);
            model.addAttribute("selectedHours", hours);
            model.addAttribute("selectedLimit", limit);

            // 获取链路追踪数据
            if (serviceName != null && !serviceName.isEmpty()) {
                var traces = traceSpanPersistenceService.getRecentSpansByService(serviceName, limit);
                model.addAttribute("traces", traces);
            } else {
                var traces = traceSpanPersistenceService.getRecentSpans(hours, limit);
                model.addAttribute("traces", traces);
            }

            return "traces";
        } catch (Exception e) {
            log.error("加载链路追踪数据失败", e);
            model.addAttribute("error", "加载链路追踪数据失败: " + e.getMessage());
            return "error";
        }
    }

    /**
     * 服务详情页面
     */
    @GetMapping("/service")
    public String serviceDetail(
            @RequestParam("name") String serviceName,
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            Model model) {

        try {
            model.addAttribute("serviceName", serviceName);
            model.addAttribute("hours", hours);

            // 获取服务详细信息
            var recentSpans = traceSpanPersistenceService.getRecentSpansByService(serviceName, 100);
            model.addAttribute("recentSpans", recentSpans);

            // 获取服务依赖关系
            var dependencies = traceSpanPersistenceService.getServiceDependencies(hours);
            var serviceDependencies = dependencies.stream()
                    .filter(dep ->
                            serviceName.equals(dep.get("source_service")) ||
                                    serviceName.equals(dep.get("target_service"))
                    )
                    .toList();
            model.addAttribute("serviceDependencies", serviceDependencies);

            return "service-detail";
        } catch (Exception e) {
            log.error("加载服务详情失败", e);
            model.addAttribute("error", "加载服务详情失败: " + e.getMessage());
            return "error";
        }
    }

    /**
     * 链路详情页面
     */
    @GetMapping("/trace")
    public String traceDetail(
            @RequestParam("id") String traceId,
            Model model) {

        try {
            var traceSpans = traceSpanPersistenceService.getTraceById(traceId);
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
            return "error";
        }
    }

    /**
     * 错误分析页面
     */
    @GetMapping("/errors")
    public String errorAnalysis(
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            Model model) {

        try {
            var errorServices = traceSpanPersistenceService.findHighErrorServices(hours);
            model.addAttribute("errorServices", errorServices);
            model.addAttribute("hours", hours);

            return "errors";
        } catch (Exception e) {
            log.error("加载错误分析数据失败", e);
            model.addAttribute("error", "加载错误分析数据失败: " + e.getMessage());
            return "error";
        }
    }

    /**
     * 关于页面
     */
    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("version", "0.1.0");
        return "about";
    }
}
