package io.github.iweidujiang.springinsight.ui.model;

import lombok.Data;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 WebSocket消息模型
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/14
 * └───────────────────────────────────────────────
 */
@Data
public class WebSocketMessage {

    private String type; // 消息类型
    private Object data; // 消息数据
    private String timestamp; // 时间戳
    private String message; // 附加消息
}
