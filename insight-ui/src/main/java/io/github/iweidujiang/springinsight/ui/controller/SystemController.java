package io.github.iweidujiang.springinsight.ui.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 系统状态控制器
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/11
 * └───────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "系统状态", description = "Spring Insight UI服务自身状态信息")
public class SystemController {
    @Value("${spring.application.name:spring-insight-api}")
    private String appName;

    @Value("${spring.application.version:0.1.0-SNAPSHOT}")
    private String appVersion;

    private final Instant startTime = Instant.now();

    @GetMapping("/info")
    @Operation(summary = "获取系统信息", description = "返回Insight UI服务的版本、运行时间等基本信息")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        long uptime = runtimeMXBean.getUptime();

        Map<String, Object> info = new HashMap<>();
        info.put("service", appName);
        info.put("version", appVersion);
        info.put("startTime", LocalDateTime.ofInstant(startTime, ZoneId.systemDefault()));
        info.put("uptime", formatUptime(uptime));
        info.put("jvmUptime", uptime);
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("availableProcessors", Runtime.getRuntime().availableProcessors());

        return ResponseEntity.ok(info);
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "服务健康状态检查端点")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now());
        health.put("service", appName);
        return ResponseEntity.ok(health);
    }

    private String formatUptime(long uptime) {
        long days = uptime / (1000 * 60 * 60 * 24);
        long hours = (uptime % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
        long minutes = (uptime % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (uptime % (1000 * 60)) / 1000;
        return String.format("%d天 %d小时 %d分 %d秒", days, hours, minutes, seconds);
    }
}
