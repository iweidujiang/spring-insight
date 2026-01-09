package io.github.iweidujiang.springinsight.agent.model;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量数据传输对象
 *
 * @author <a href="https://github.com/iweidujiang">...</a>
 * @since 2026/1/7
 */
@Data
public class TraceBatchReport {
    /** 上报数据的服务名称 */
    private String serviceName;
    /** 上报数据的服务实例 */
    private String serviceInstance;
    /** 上报时间 */
    private Instant reportTime = Instant.now();
    /** Span列表 */
    private List<TraceSpan> traceSpans = new ArrayList<>();

    /**
     * 添加一个Span到批量中
     */
    public void addSpan(TraceSpan traceSpan) {
        this.traceSpans.add(traceSpan);
    }

    /**
     * 批量添加Span
     */
    public void addAllSpans(List<TraceSpan> traceSpans) {
        this.traceSpans.addAll(traceSpans);
    }
}
