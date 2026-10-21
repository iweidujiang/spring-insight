package io.github.iweidujiang.springinsight.agent.boot2.autoconfigure;

import io.github.iweidujiang.springinsight.agent.boot2.instrumentation.InsightJdbcProxies;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * JDBC 追踪：包装 Spring 容器中的 {@link DataSource}（默认关闭）。
 * <p>
 * 开启条件：{@code spring.insight.jdbc-tracing-enabled=true}，
 * 或兼容旧键 {@code spring.insight.db-metrics.enabled=true}。
 * </p>
 *
 * @since 2026-10-11
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Configuration
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InsightJdbcAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InsightJdbcAutoConfiguration.class);

    /**
     * @param properties  配置
     * @param listener    上报
     * @param environment 用于读旧键 db-metrics.enabled
     * @return DataSource 包装器；未开启时不做包装
     */
    @Bean
    public static BeanPostProcessor insightJdbcDataSourcePostProcessor(
            InsightBoot2Properties properties,
            SpanReportingListener listener,
            Environment environment) {
        boolean enabled = properties.isJdbcTracingEnabled()
                || Boolean.TRUE.equals(environment.getProperty("spring.insight.db-metrics.enabled", Boolean.class, Boolean.FALSE));
        if (!enabled) {
            return new BeanPostProcessor() {};
        }
        if (!properties.isJdbcTracingEnabled()) {
            properties.setJdbcTracingEnabled(true);
        }
        log.info("[JDBC] 已启用 DataSource 包装（慢阈值={}ms；错误始终上报）",
                properties.getJdbcSlowThresholdMs());
        return new InsightJdbcDataSourcePostProcessor(listener, properties);
    }

    private static final class InsightJdbcDataSourcePostProcessor implements BeanPostProcessor {
        private final SpanReportingListener listener;
        private final InsightBoot2Properties properties;

        InsightJdbcDataSourcePostProcessor(SpanReportingListener listener, InsightBoot2Properties properties) {
            this.listener = listener;
            this.properties = properties;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (!(bean instanceof DataSource)) {
                return bean;
            }
            DataSource wrapped = InsightJdbcProxies.wrap((DataSource) bean, listener, properties);
            if (wrapped != bean) {
                log.debug("[JDBC] 包装 DataSource bean={}", beanName);
            }
            return wrapped;
        }
    }
}
