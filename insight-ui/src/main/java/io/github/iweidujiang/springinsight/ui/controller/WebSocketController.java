package io.github.iweidujiang.springinsight.ui.controller;

import io.github.iweidujiang.springinsight.ui.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 WebSocket控制器
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/14
 * └───────────────────────────────────────────────
 */
@Slf4j
@Controller
public class WebSocketController {

    private final WebSocketService webSocketService;

    public WebSocketController(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    /**
     * 客户端订阅连接
     */
    @SubscribeMapping("/topic/stats")
    public Map<String, Object> onSubscribe() {
        webSocketService.onClientConnect();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "connected");
        response.put("message", "成功连接到实时数据服务");
        response.put("timestamp", Instant.now().toString());

        return response;
    }

    /**
     * 客户端发送命令
     */
    @MessageMapping("/command")
    @SendTo("/topic/commands")
    public Map<String, Object> handleCommand(Map<String, Object> command) {
        String type = (String) command.get("type");
        log.info("收到WebSocket命令: {}", type);

        Map<String, Object> response = new HashMap<>();
        response.put("type", type);
        response.put("status", "processed");
        response.put("timestamp", Instant.now().toString());

        if ("REFRESH".equals(type)) {
            // 刷新数据缓存
            // dataCollectorService.clearCache();
            response.put("message", "数据缓存已刷新");
        } else if ("GET_CONNECTIONS".equals(type)) {
            response.put("connections", webSocketService.getConnectionCount());
        }

        return response;
    }
}
