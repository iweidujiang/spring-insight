package io.github.iweidujiang.springinsight.server.metrics;

import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * 将存储容量相关指标绑定到 Micrometer（Actuator / Prometheus）。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Component
public class StorageCapacityMetrics implements MeterBinder {

    private final TraceSpanPersistenceService persistenceService;
    private final InsightServerStorageProperties storageProperties;

    /**
     * @param persistenceService 存储门面
     * @param storageProperties  容量上限配置
     */
    public StorageCapacityMetrics(TraceSpanPersistenceService persistenceService,
                                  InsightServerStorageProperties storageProperties) {
        this.persistenceService = persistenceService;
        this.storageProperties = storageProperties;
    }

    /**
     * 注册 spans 持有量、上限、驱逐累计。
     *
     * @param registry MeterRegistry
     */
    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("spring.insight.server.spans.stored", persistenceService,
                        TraceSpanPersistenceService::getStoredSpanCount)
                .description("Current span count held by insight-server storage")
                .register(registry);
        Gauge.builder("spring.insight.server.spans.max", storageProperties,
                        InsightServerStorageProperties::getMaxSpans)
                .description("Configured max span capacity")
                .register(registry);
        Gauge.builder("spring.insight.server.spans.evicted", persistenceService,
                        s -> (double) s.getEvictedSpanCount())
                .description("Cumulative spans evicted by count/time retention")
                .register(registry);
    }
}
