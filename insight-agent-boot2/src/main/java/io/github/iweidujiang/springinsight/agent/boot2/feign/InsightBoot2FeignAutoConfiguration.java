/**
 * Boot2 Feign 出站追踪：注册公开 Capability，兼容 Feign 子上下文与 url 直连。
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
     * 注册公开 Capability Bean；Spring Cloud OpenFeign 会继承到每个 Feign 子上下文。
     *
     * @param insightProperties     配置
     * @param spanReportingListener 上报入口
     * @return Capability
     */
    @Bean
    public Capability insightBoot2FeignCapability(
            ObjectProvider<InsightBoot2Properties> insightProperties,
            ObjectProvider<SpanReportingListener> spanReportingListener) {
        return new InsightBoot2FeignCapability(insightProperties, spanReportingListener);
    }
}
