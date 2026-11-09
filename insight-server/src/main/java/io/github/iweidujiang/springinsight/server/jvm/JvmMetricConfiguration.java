package io.github.iweidujiang.springinsight.server.jvm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JVM 指标存储装配。
 *
 * @author 苏渡苇
 * @since 2026-11-09
 */
@Configuration
public class JvmMetricConfiguration {

    /**
     * @return 进程内环形缓冲实现
     */
    @Bean
    public JvmMetricStore jvmMetricStore() {
        return new InMemoryJvmMetricStore();
    }
}
