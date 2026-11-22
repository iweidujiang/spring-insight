package io.github.iweidujiang.springinsight.agent.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────┐
 * │ 📦 批量上报的数据包
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────┘
 */
@Data
public class TraceBatchReport {
    /** 上报的服务名称 */
    private String serviceName;

    /** 上报的服务实例 */
    private String serviceInstance;

    /**
     * Agent / Starter 版本（如 {@code 0.7.0-SNAPSHOT}）。
     */
    private String agentVersion;

    /**
     * 上报协议号（如 {@code 1}）。
     */
    private String protocol;

    /** 上报时间 */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant reportTime = Instant.now();

    /** 批次ID（用于去重和追踪） */
    private String batchId;

    /** Span 列表 */
    private List<TraceSpan> spans = new ArrayList<>();

    /** 元数据信息 */
    private Metadata metadata = new Metadata();

    /**
     * 添加一个 Span
     */
    public void addSpan(TraceSpan span) {
        if (span != null) {
            this.spans.add(span);
        }
    }

    /**
     * 批量添加 Span
     */
    public void addAllSpans(List<TraceSpan> spans) {
        if (spans != null && !spans.isEmpty()) {
            this.spans.addAll(spans);
        }
    }

    /**
     * 获取 Span 数量
     */
    public int getSpanCount() {
        return spans.size();
    }

    /**
     * 清空 Span 列表
     */
    public void clearSpans() {
        this.spans.clear();
    }

    /**
     * 元数据类
     */
    @Data
    public static class Metadata {
        /**
         * SDK / Agent 版本（与顶层 {@code agentVersion} 对齐，兼容旧字段名）。
         */
        private String sdkVersion;

        /**
         * Agent 版本（与顶层一致；旧 Server 可读 metadata）。
         */
        private String agentVersion;

        /** 上报协议版本（与顶层 {@code protocol} 对齐） */
        private String protocolVersion;

        /** 上报客户端类型 */
        private String clientType = "java-agent";

        /** 环境信息 */
        private String environment = "default";

        /** 其他扩展信息 */
        private Map<String, String> extensions = new HashMap<>();
    }
}
