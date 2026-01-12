package io.github.iweidujiang.springinsight.ui.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 API 调用服务类
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/12
 * └───────────────────────────────────────────────
 */
@Slf4j
@Service
public class ApiService {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring-insight.collector.url:http://localhost:8080}")
    private String collectorUrl;

    /**
     * 获取所有服务名称
     */
    public List<String> getAllServiceNames() {
        try {
            // 直接从storage获取服务名称
            // 这里简化处理，实际应该通过collector的API获取
            return List.of("demo-service");
        } catch (Exception e) {
            log.error("获取服务名称失败", e);
            return List.of();
        }
    }

    /**
     * 获取服务依赖关系
     */
    public List<Map<String, Object>> getServiceDependencies(int hours) {
        try {
            // 调用collector的统计接口
            String url = collectorUrl + "/api/v1/stats";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            // 这里应该返回实际的依赖关系数据
            // 暂时返回空列表
            return Arrays.asList();
        } catch (Exception e) {
            log.error("获取服务依赖关系失败", e);
            return Arrays.asList();
        }
    }

    /**
     * 获取实时统计
     */
    public Map<String, Object> getRealtimeStats() {
        try {
            String url = collectorUrl + "/api/v1/stats";
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.error("获取实时统计失败", e);
            return Map.of(
                    "totalReceivedSpans", 0,
                    "errorRate", "0%",
                    "avgResponseTime", "0ms"
            );
        }
    }
}
