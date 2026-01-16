package io.github.iweidujiang.springinsight.ui.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.ui.service.DataCollectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 调试页面
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/16
 * └───────────────────────────────────────────────
 */
@Slf4j
@Controller
public class DebugViewController {
    private final DataCollectorService dataCollectorService;
    private final ObjectMapper objectMapper;

    public DebugViewController(DataCollectorService dataCollectorService, ObjectMapper objectMapper) {
        this.dataCollectorService = dataCollectorService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/debug/view")
    public String debugView(Model model) {
        try {
            // 收集所有数据
            Map<String, Object> allData = new HashMap<>();
            allData.put("services", dataCollectorService.getServiceNames());
            allData.put("serviceStats", dataCollectorService.getServiceStats());
            allData.put("dependencies", dataCollectorService.getServiceDependencies(24));
            allData.put("errorAnalysis", dataCollectorService.getErrorAnalysis(24));
            allData.put("collectorStats", dataCollectorService.getCollectorStats());

            // 传递给模板
            model.addAttribute("services", allData.get("services"));
            model.addAttribute("serviceStats", allData.get("serviceStats"));
            model.addAttribute("dependencies", allData.get("dependencies"));
            model.addAttribute("errorAnalysis", allData.get("errorAnalysis"));
            model.addAttribute("collectorStats", allData.get("collectorStats"));

            // 添加调试信息
            model.addAttribute("collectorUrl", dataCollectorService.getCollectorUrl());
            model.addAttribute("cacheSize", dataCollectorService.getCacheSize());
            model.addAttribute("rawJson", objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(allData));

            return "debug";

        } catch (Exception e) {
            log.error("加载调试页面失败", e);
            model.addAttribute("error", e.getMessage());
            return "debug";
        }
    }
}
