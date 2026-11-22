package io.github.iweidujiang.springinsight.server.presence;

import io.github.iweidujiang.springinsight.collector.model.AgentHeartbeatRequest;
import io.github.iweidujiang.springinsight.collector.model.CollectorRequest;
import io.github.iweidujiang.springinsight.collector.model.JvmMetricBatchRequest;
import org.springframework.util.StringUtils;

/**
 * 从各类上报请求中解析 Agent 版本 / 协议并写入缓存。
 *
 * @author 苏渡苇
 * @since 2026-11-22
 */
public final class ReportedAgentMetaSupport {

    private ReportedAgentMetaSupport() {
    }

    /**
     * 从 Span 批次请求记录版本。
     *
     * @param store   缓存
     * @param request 批次
     */
    public static void record(ReportedAgentMetaStore store, CollectorRequest request) {
        if (store == null || request == null) {
            return;
        }
        String version = firstNonBlank(
                request.getAgentVersion(),
                request.getMetadata() != null ? request.getMetadata().getAgentVersion() : null,
                request.getMetadata() != null ? request.getMetadata().getSdkVersion() : null);
        String protocol = firstNonBlank(
                request.getProtocol(),
                request.getMetadata() != null ? request.getMetadata().getProtocolVersion() : null);
        store.record(request.getServiceName(), request.getServiceInstance(), version, protocol);
    }

    /**
     * 从 JVM 批次请求记录版本。
     *
     * @param store   缓存
     * @param request 批次
     */
    public static void record(ReportedAgentMetaStore store, JvmMetricBatchRequest request) {
        if (store == null || request == null) {
            return;
        }
        store.record(request.getServiceName(), request.getServiceInstance(),
                request.getAgentVersion(), request.getProtocol());
    }

    /**
     * 从心跳请求记录版本。
     *
     * @param store   缓存
     * @param request 心跳
     */
    public static void record(ReportedAgentMetaStore store, AgentHeartbeatRequest request) {
        if (store == null || request == null) {
            return;
        }
        store.record(request.getServiceName(), request.getServiceInstance(),
                request.getAgentVersion(), request.getProtocol());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return null;
    }
}
