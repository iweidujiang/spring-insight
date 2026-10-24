package io.github.iweidujiang.springinsight.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.collector.service.TraceSpanCollectorService;
import io.github.iweidujiang.springinsight.server.anomaly.AnomalyDetector;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.server.topology.TopologyDiffService;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import io.github.iweidujiang.springinsight.storage.service.TraceContextExportService;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import io.github.iweidujiang.springinsight.storage.service.TroubleshootPackExportService;
import io.github.iweidujiang.springinsight.storage.spi.SpanStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

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

    /**
     * @param spanStore 存储 SPI
     * @return Trace Context 导出
     */
    @Bean
    public TraceContextExportService traceContextExportService(SpanStore spanStore) {
        return new TraceContextExportService(spanStore);
    }

    /**
     * @param contextExport   Trace Context
     * @param persistence     持久化
     * @param environment     环境
     * @param buildProperties build-info（可空）
     * @return 排障包导出
     */
    @Bean
    public TroubleshootPackExportService troubleshootPackExportService(
            TraceContextExportService contextExport,
            TraceSpanPersistenceService persistence,
            Environment environment,
            org.springframework.beans.factory.ObjectProvider<BuildProperties> buildProperties) {
        return new TroubleshootPackExportService(
                contextExport, persistence, environment, buildProperties.getIfAvailable());
    }

    /**
     * @param persistence 持久化
     * @return 采集服务
     */
    @Bean
    public TraceSpanCollectorService traceSpanCollectorService(TraceSpanPersistenceService persistence) {
        return new TraceSpanCollectorService(persistence);
    }

    /**
     * @return 无阈值异常检测（CollectorApiController 依赖）
     */
    @Bean
    public AnomalyDetector anomalyDetector() {
        return new AnomalyDetector();
    }

    /**
     * @param persistence 持久化
     * @return 拓扑时段对比
     */
    @Bean
    public TopologyDiffService topologyDiffService(TraceSpanPersistenceService persistence) {
        return new TopologyDiffService(persistence);
    }
}
