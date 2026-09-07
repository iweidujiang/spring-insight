/**
 * Boot2 Feign 出站追踪：通过 Capability 包装 Client（兼容 Feign 子上下文），上报 CLIENT Span。
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass({Client.class, Capability.class})
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightBoot2FeignAutoConfiguration {

    /**
     * Feign Capability：在构建每个 FeignClient 时 enrich Client，避免仅包装父容器 Bean 却未进入子上下文的问题。
     *
     * @param insightProperties     配置
     * @param spanReportingListener 上报入口
     * @return Capability
     */
    @Bean
    public Capability insightBoot2FeignCapability(
            final ObjectProvider<InsightBoot2Properties> insightProperties,
            final ObjectProvider<SpanReportingListener> spanReportingListener) {
        return new Capability() {
            /**
             * 包装底层 Client；已包装则跳过，防止重复套娃。
             *
             * @param client 原 Client（可能是 Default 或 LoadBalancer）
             * @return TracingFeignClient
             */
            @Override
            public Client enrich(Client client) {
                if (client instanceof TracingFeignClient) {
                    return client;
                }
                return new TracingFeignClient(client, insightProperties, spanReportingListener);
            }
        };
    }
}
