package io.github.iweidujiang.springinsight.server.alert;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次告警的字段集合，与原先 Webhook JSON 的键一致。
 *
 * @param fields 按写入顺序保留的字段；允许空值
 */
public record AlertPayload(Map<String, Object> fields) {

    public AlertPayload {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (fields != null) {
            copy.putAll(fields);
        }
        fields = copy;
    }
}
