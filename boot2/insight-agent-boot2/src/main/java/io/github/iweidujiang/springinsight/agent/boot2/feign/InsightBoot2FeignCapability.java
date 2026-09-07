/**
 * Boot2 Feign Capability：包装 Client 以上报带 remoteService 的 CLIENT Span。
 * <p>
 * 必须是 public 具名类：Feign {@code Capability.invoke} 通过反射调用 enrich，
 * 匿名类在 JDK 9+ 会抛 IllegalAccessException（Unable to enrich Client$Default）。
 * </p>
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.feign;

import feign.Capability;
import feign.Client;
import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 公开 Capability 实现，供 Feign 反射 enrich 使用。
 */
public class InsightBoot2FeignCapability implements Capability {

    /** Insight 配置（延迟获取） */
    private final ObjectProvider<InsightBoot2Properties> insightProperties;

    /** Span 上报入口（延迟获取） */
    private final ObjectProvider<SpanReportingListener> spanReportingListener;

    /**
     * @param insightProperties     配置 Provider
     * @param spanReportingListener 上报 Provider
     */
    public InsightBoot2FeignCapability(ObjectProvider<InsightBoot2Properties> insightProperties,
                                       ObjectProvider<SpanReportingListener> spanReportingListener) {
        this.insightProperties = insightProperties;
        this.spanReportingListener = spanReportingListener;
    }

    /**
     * 包装底层 Client；已包装则跳过，防止重复套娃。
     *
     * @param client 原 Client（Default 或 LoadBalancer）
     * @return TracingFeignClient 或原 Client
     */
    @Override
    public Client enrich(Client client) {
        if (client instanceof TracingFeignClient) {
            return client;
        }
        return new TracingFeignClient(client, insightProperties, spanReportingListener);
    }
}
