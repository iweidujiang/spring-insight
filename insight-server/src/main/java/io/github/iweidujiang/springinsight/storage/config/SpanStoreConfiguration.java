package io.github.iweidujiang.springinsight.storage.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.FileSpanStore;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import io.github.iweidujiang.springinsight.storage.impl.SqliteSpanStore;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 {@code spring.insight.server.storage.mode} 装配 {@link SpanStore}。
 *
 * @since 2026-09-13
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@Configuration
public class SpanStoreConfiguration {

    /**
     * @param properties   存储配置
     * @param objectMapper JSON
     * @return 对应模式的 SpanStore
     */
    @Bean(destroyMethod = "close")
    public SpanStore spanStore(InsightServerStorageProperties properties, ObjectMapper objectMapper) {
        String mode = properties.normalizedMode();
        return switch (mode) {
            case "file" -> new FileSpanStore(properties, objectMapper);
            case "sqlite" -> new SqliteSpanStore(properties, objectMapper);
            default -> {
                log.info("[存储] 模式=memory，maxSpans={}（重启将清空）", properties.getMaxSpans());
                yield new InMemorySpanStore(properties, "memory");
            }
        };
    }
}
