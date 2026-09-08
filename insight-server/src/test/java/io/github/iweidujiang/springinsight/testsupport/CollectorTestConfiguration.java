package io.github.iweidujiang.springinsight.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.collector.service.TraceSpanCollectorService;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Collector 切片测试配置（包路径刻意不在主应用 scanBasePackages 内，避免被全量启动测试扫到）。
 *
 * @since 2026-09-08
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@SpringBootConfiguration
@ComponentScan(basePackages = {
        "io.github.iweidujiang.springinsight.collector.controller",
        "io.github.iweidujiang.springinsight.collector.exception"
})
public class CollectorTestConfiguration {

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
     * @param objectMapper      JSON
     * @return 持久化服务
     */
    @Bean
    public TraceSpanPersistenceService traceSpanPersistenceService(
            InsightServerStorageProperties storageProperties,
            ObjectMapper objectMapper) {
        return new TraceSpanPersistenceService(storageProperties, objectMapper);
    }

    /**
     * @param persistence 持久化
     * @return 采集服务
     */
    @Bean
    public TraceSpanCollectorService traceSpanCollectorService(TraceSpanPersistenceService persistence) {
        return new TraceSpanCollectorService(persistence);
    }
}
