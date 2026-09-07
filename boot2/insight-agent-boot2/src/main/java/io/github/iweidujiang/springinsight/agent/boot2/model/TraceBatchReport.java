/*
 * Copyright (c) 2026, 苏渡苇. All rights reserved.
 *
 * TraceBatchReport：批量上报请求体，字段与主线 / insight-server CollectorRequest 对齐。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class TraceBatchReport {

    private String serviceName;
    private String serviceInstance;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant reportTime = Instant.now();

    private String batchId;
    private List<TraceSpan> spans = new ArrayList<TraceSpan>();
    private Metadata metadata = new Metadata();

    public void addAllSpans(List<TraceSpan> list) {
        if (list != null && !list.isEmpty()) {
            this.spans.addAll(list);
        }
    }

    @Data
    public static class Metadata {
        private String sdkVersion = "spring-insight/0.1.0-boot2-SNAPSHOT";
        private String protocolVersion = "1.0";
        private String clientType = "java-agent-boot2";
        private String environment = "default";
        private Map<String, String> extensions = new HashMap<String, String>();
    }
}
