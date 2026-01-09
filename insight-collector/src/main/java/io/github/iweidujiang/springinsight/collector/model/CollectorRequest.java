package io.github.iweidujiang.springinsight.collector.model;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 数据上报请求模型
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Slf4j
@Data
public class CollectorRequest {
    /** 服务名称 */
    @NotBlank(message = "服务名称不能为空")
    private String serviceName;

    /** 服务实例标识 */
    @NotBlank(message = "服务实例标识不能为空")
    private String serviceInstance;

    /** 批次ID（用于去重和追踪） */
    private String batchId;

    /** 上报时间 */
    private Instant reportTime = Instant.now();

    /** Span 列表 */
    @NotNull(message = "Span列表不能为空")
    @Valid
    private List<TraceSpan> spans;

    /** 元数据信息 */
    private Metadata metadata;

    /**
     * 验证请求的合法性
     */
    public boolean isValid() {
        if (spans == null || spans.isEmpty()) {
            log.warn("[Collector请求] Span列表为空，忽略无效请求");
            return false;
        }

        // 验证每个Span的基本字段
        for (int i = 0; i < spans.size(); i++) {
            TraceSpan span = spans.get(i);
            if (span.getTraceId() == null || span.getTraceId().trim().isEmpty()) {
                log.warn("[Collector请求] 第{}个Span缺少traceId", i + 1);
                return false;
            }
            if (span.getSpanId() == null || span.getSpanId().trim().isEmpty()) {
                log.warn("[Collector请求] 第{}个Span缺少spanId", i + 1);
                return false;
            }
        }

        return true;
    }

    /**
     * 获取请求摘要信息（用于日志）
     */
    public String getSummary() {
        return String.format("service=%s, instance=%s, spanCount=%d, batchId=%s",
                serviceName, serviceInstance, spans != null ? spans.size() : 0, batchId);
    }

    /**
     * 元数据
     */
    @Data
    public static class Metadata {
        /** SDK版本 */
        private String sdkVersion;

        /** 协议版本 */
        private String protocolVersion;

        /** 环境信息 */
        private String environment;

        /** 扩展信息 */
        private java.util.Map<String, String> extensions;
    }
}
