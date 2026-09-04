package io.github.iweidujiang.springinsight.storage;

import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 存储模块测试用最小配置：仅注册内存持久化 Bean，不依赖已删除的独立启动类。
 */
@SpringBootConfiguration
public class StorageTestConfiguration {

    /**
     * @return 进程内 Span 存储服务
     */
    @Bean
    public TraceSpanPersistenceService traceSpanPersistenceService() {
        return new TraceSpanPersistenceService();
    }
}
