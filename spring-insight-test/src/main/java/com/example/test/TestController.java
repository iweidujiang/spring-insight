package com.example.test;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 测试控制器，用于模拟不同的请求场景
 */
@RestController
public class TestController {

    /**
     * 简单的健康检查端点
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    /**
     * 测试正常请求
     */
    @GetMapping("/test/normal")
    public String normalRequest() {
        return "正常请求响应";
    }

    /**
     * 测试延迟请求
     */
    @GetMapping("/test/delay")
    public String delayRequest() throws InterruptedException {
        // 模拟1秒延迟
        TimeUnit.SECONDS.sleep(1);
        return "延迟请求响应";
    }

    /**
     * 测试错误请求
     */
    @GetMapping("/test/error")
    public String errorRequest() {
        throw new RuntimeException("模拟错误请求");
    }

    /**
     * 测试路径参数
     */
    @GetMapping("/test/param/{id}")
    public String paramRequest(@PathVariable Long id) {
        return "路径参数请求响应: " + id;
    }

}
