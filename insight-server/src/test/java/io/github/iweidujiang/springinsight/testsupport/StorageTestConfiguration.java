package io.github.iweidujiang.springinsight.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Storage 切片测试配置（不在主应用扫描包内，避免与 Collector 测试配置撞名）。
 *
 * @since 2026-09-08
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@SpringBootConfiguration
public class StorageTestConfiguration {

    /**
     * @return 默认内存存储配置
     */
    @Bean
    public InsightServerStorageProperties insightServerStorageProperties() {
        return new InsightServerStorageProperties();
    }

    /**
     * @return Jackson ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * @param storageProperties 存储配置
     * @return 内存 SpanStore
     */
    @Bean
    public SpanStore spanStore(InsightServerStorageProperties storageProperties) {
        return new InMemorySpanStore(storageProperties, "memory");
    }

    /**
     * @param spanStore 存储 SPI
     * @return 持久化门面
     */
    @Bean
    public TraceSpanPersistenceService traceSpanPersistenceService(SpanStore spanStore) {
        return new TraceSpanPersistenceService(spanStore);
    }
}
