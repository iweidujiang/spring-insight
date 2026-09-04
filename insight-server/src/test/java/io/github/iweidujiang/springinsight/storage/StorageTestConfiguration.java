package io.github.iweidujiang.springinsight.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 存储模块测试用最小配置：仅注册内存持久化 Bean，不依赖已删除的独立启动类。
 */
@SpringBootConfiguration
public class StorageTestConfiguration {

    @Bean
    public InsightServerStorageProperties insightServerStorageProperties() {
        return new InsightServerStorageProperties();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public TraceSpanPersistenceService traceSpanPersistenceService(
            InsightServerStorageProperties storageProperties,
            ObjectMapper objectMapper) {
        return new TraceSpanPersistenceService(storageProperties, objectMapper);
    }
}
