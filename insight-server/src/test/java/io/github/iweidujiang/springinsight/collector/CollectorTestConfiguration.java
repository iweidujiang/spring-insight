package io.github.iweidujiang.springinsight.collector;

import io.github.iweidujiang.springinsight.collector.service.TraceSpanCollectorService;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Collector 模块测试配置：扫描 controller 并手工提供 storage / service Bean。
 * <p>
 * 替代已删除的 {@code InsightCollectorApplication}（可运行入口已统一到 insight-server）。
 * </p>
 */
@SpringBootConfiguration
@ComponentScan(basePackages = {
        "io.github.iweidujiang.springinsight.collector.controller",
        "io.github.iweidujiang.springinsight.collector.exception"
})
public class CollectorTestConfiguration {

    /**
     * @return 内存 Span 存储
     */
    @Bean
    public TraceSpanPersistenceService traceSpanPersistenceService() {
        return new TraceSpanPersistenceService();
    }

    /**
     * @param persistence 存储依赖
     * @return 收集业务服务
     */
    @Bean
    public TraceSpanCollectorService traceSpanCollectorService(TraceSpanPersistenceService persistence) {
        return new TraceSpanCollectorService(persistence);
    }
}
