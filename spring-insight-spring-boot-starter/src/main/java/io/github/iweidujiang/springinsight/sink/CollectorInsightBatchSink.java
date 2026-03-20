package io.github.iweidujiang.springinsight.sink;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.model.JvmMetric;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.agent.sink.InsightBatchSink;
import io.github.iweidujiang.springinsight.collector.model.CollectorRequest;
import io.github.iweidujiang.springinsight.collector.service.TraceSpanCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 将 Agent 异步队列中的数据交给 Collector 服务落库
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/3/20
 * └───────────────────────────────────────────────
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorInsightBatchSink implements InsightBatchSink {

    private final TraceSpanCollectorService traceSpanCollectorService;
    private final InsightProperties properties;

    @Override
    public void acceptTraceSpans(List<TraceSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return;
        }
        CollectorRequest request = new CollectorRequest();
        request.setServiceName(properties.getServiceName());
        request.setServiceInstance(resolveServiceInstance());
        request.setBatchId(UUID.randomUUID().toString());
        request.setSpans(spans);

        var response = traceSpanCollectorService.processBatchRequest(request);
        if (!response.isSuccess()) {
            log.warn("[收集链路] 批量写入内存未成功: {}", response.getMessage());
        }
    }

    @Override
    public void acceptJvmMetrics(List<JvmMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        log.debug("[收集链路] JVM 指标批次已接收（暂无专用存储表）: size={}", metrics.size());
    }

    private String resolveServiceInstance() {
        String si = properties.getServiceInstance();
        if (si != null && !si.isBlank()) {
            return si;
        }
        String port = System.getProperty("server.port", "8080");
        if ("0".equals(port)) {
            port = "8080";
        }
        return "localhost:" + port;
    }
}
