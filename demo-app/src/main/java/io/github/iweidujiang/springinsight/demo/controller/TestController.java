package io.github.iweidujiang.springinsight.demo.controller;

import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 测试控制器
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/trace")
    public Map<String, Object> testTrace() {
        log.info("测试追踪端点被调用");

        Map<String, Object> result = new HashMap<>();

        // 测试1：检查TraceContext是否可用
        try {
            TraceSpan span = TraceContext.startSpan("测试手动创建Span");
            result.put("traceId", span.getTraceId());
            result.put("spanId", span.getSpanId());

            Thread.sleep(100); // 模拟处理

            TraceContext.endSpan();
            result.put("traceSuccess", true);
            result.put("message", "TraceContext 正常工作");

        } catch (Exception e) {
            result.put("traceSuccess", false);
            result.put("error", e.getMessage());
            log.error("TraceContext 测试失败", e);
        }

        // 测试2：返回当前类加载器信息
        result.put("agentClassLoaded", checkIfAgentClassLoaded());
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }

    @GetMapping("/config")
    public Map<String, Object> showConfig() {
        Map<String, Object> config = new HashMap<>();

        // 显示所有相关配置
        config.put("java.version", System.getProperty("java.version"));
        config.put("spring.profiles.active", System.getProperty("spring.profiles.active"));

        // 检查配置属性文件
        config.put("configCheck", "检查 application.yml 中的 spring.insight 配置");

        return config;
    }

    private boolean checkIfAgentClassLoaded() {
        try {
            Class.forName("io.github.iweidujiang.springinsight.agent.context.TraceContext");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
