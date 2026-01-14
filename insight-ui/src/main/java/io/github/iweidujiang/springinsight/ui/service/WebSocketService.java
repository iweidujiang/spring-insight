package io.github.iweidujiang.springinsight.ui.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.ui.model.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 WebSocket服务
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/14
 * └───────────────────────────────────────────────
 */
@Slf4j
@Service
public class WebSocketService {
    private final SimpMessagingTemplate messagingTemplate;
    private final DataCollectorService dataCollectorService;
    private final ObjectMapper objectMapper;

    private final AtomicLong connectionCount = new AtomicLong(0);
    private final Map<String, Long> lastUpdateTimes = new HashMap<>();

    public WebSocketService(SimpMessagingTemplate messagingTemplate,
                            DataCollectorService dataCollectorService) {
        this.messagingTemplate = messagingTemplate;
        this.dataCollectorService = dataCollectorService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 广播实时统计信息（每5秒一次）
     */
    @Scheduled(fixedDelay = 5000)
    public void broadcastStats() {
        if (connectionCount.get() == 0) return;

        try {
            // 获取最新数据
            var collectorStats = dataCollectorService.getCollectorStats();
            var serviceStats = dataCollectorService.getServiceStats();
            var errorAnalysis = dataCollectorService.getErrorAnalysis(1); // 最近1小时

            // 构建消息
            Map<String, Object> data = new HashMap<>();
            data.put("collectorStats", collectorStats);
            data.put("serviceStats", serviceStats.subList(0, Math.min(5, serviceStats.size())));
            data.put("errorAnalysis", errorAnalysis.subList(0, Math.min(5, errorAnalysis.size())));
            data.put("timestamp", Instant.now().toString());
            data.put("cacheSize", dataCollectorService.getCacheSize());

            WebSocketMessage message = new WebSocketMessage();
            message.setType("STATS_UPDATE");
            message.setData(data);

            // 广播消息
            messagingTemplate.convertAndSend("/topic/stats", message);
            log.debug("广播实时统计信息");

        } catch (Exception e) {
            log.error("广播实时统计信息失败", e);
        }
    }

    /**
     * 广播拓扑图更新（每10秒一次）
     */
    @Scheduled(fixedDelay = 10000)
    public void broadcastTopology() {
        if (connectionCount.get() == 0) return;

        try {
            var dependencies = dataCollectorService.getServiceDependencies(1); // 最近1小时

            WebSocketMessage message = new WebSocketMessage();
            message.setType("TOPOLOGY_UPDATE");
            message.setData(dependencies);

            messagingTemplate.convertAndSend("/topic/topology", message);
            log.debug("广播拓扑图更新");

        } catch (Exception e) {
            log.error("广播拓扑图更新失败", e);
        }
    }

    /**
     * 广播最近链路（每15秒一次）
     */
    @Scheduled(fixedDelay = 15000)
    public void broadcastRecentTraces() {
        if (connectionCount.get() == 0) return;

        try {
            var recentSpans = dataCollectorService.getRecentSpans(1, 20); // 最近1小时，20条

            WebSocketMessage message = new WebSocketMessage();
            message.setType("TRACES_UPDATE");
            message.setData(recentSpans);

            messagingTemplate.convertAndSend("/topic/traces", message);
            log.debug("广播最近链路");

        } catch (Exception e) {
            log.error("广播最近链路失败", e);
        }
    }

    /**
     * 广播错误告警（实时）
     */
    public void broadcastErrorAlert(String serviceName, String errorMessage, String level) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("serviceName", serviceName);
            alert.put("errorMessage", errorMessage);
            alert.put("level", level);
            alert.put("timestamp", Instant.now().toString());

            WebSocketMessage message = new WebSocketMessage();
            message.setType("ERROR_ALERT");
            message.setData(alert);

            messagingTemplate.convertAndSend("/topic/alerts", message);
            log.info("广播错误告警: {} - {}", serviceName, errorMessage);

        } catch (Exception e) {
            log.error("广播错误告警失败", e);
        }
    }

    /**
     * 客户端连接
     */
    public void onClientConnect() {
        long count = connectionCount.incrementAndGet();
        log.info("WebSocket客户端连接，当前连接数: {}", count);
    }

    /**
     * 客户端断开
     */
    public void onClientDisconnect() {
        long count = connectionCount.decrementAndGet();
        if (count < 0) {
            connectionCount.set(0);
        }
        log.info("WebSocket客户端断开，当前连接数: {}", Math.max(0, count));
    }

    /**
     * 获取当前连接数
     */
    public long getConnectionCount() {
        return connectionCount.get();
    }
}
