/**
 * TraceBatchReport：批量上报请求体，字段与主线 / insight-server CollectorRequest 对齐。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TraceBatchReport {

    private String serviceName;
    private String serviceInstance;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant reportTime = Instant.now();

    private String batchId;
    private List<TraceSpan> spans = new ArrayList<TraceSpan>();
    private Metadata metadata = new Metadata();

    /**
     * 批量追加 Span。
     *
     * @param list Span 列表；null/空则忽略
     */
    public void addAllSpans(List<TraceSpan> list) {
        if (list != null && !list.isEmpty()) {
            this.spans.addAll(list);
        }
    }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getServiceInstance() { return serviceInstance; }
    public void setServiceInstance(String serviceInstance) { this.serviceInstance = serviceInstance; }
    public Instant getReportTime() { return reportTime; }
    public void setReportTime(Instant reportTime) { this.reportTime = reportTime; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public List<TraceSpan> getSpans() { return spans; }
    public void setSpans(List<TraceSpan> spans) { this.spans = spans; }
    public Metadata getMetadata() { return metadata; }
    public void setMetadata(Metadata metadata) { this.metadata = metadata; }

    /**
     * 上报元数据。
     */
    public static class Metadata {
        private String sdkVersion = "spring-insight/0.1.0-boot2";
        private String protocolVersion = "1.0";
        private String clientType = "java-agent-boot2";
        private String environment = "default";
        private Map<String, String> extensions = new HashMap<String, String>();

        public String getSdkVersion() { return sdkVersion; }
        public void setSdkVersion(String sdkVersion) { this.sdkVersion = sdkVersion; }
        public String getProtocolVersion() { return protocolVersion; }
        public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }
        public String getClientType() { return clientType; }
        public void setClientType(String clientType) { this.clientType = clientType; }
        public String getEnvironment() { return environment; }
        public void setEnvironment(String environment) { this.environment = environment; }
        public Map<String, String> getExtensions() { return extensions; }
        public void setExtensions(Map<String, String> extensions) { this.extensions = extensions; }
    }
}
