package io.github.iweidujiang.springinsight.server.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构化 AI 结果解析与 nav  enrichment。
 *
 * @since 2026-09-24
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class InsightAiExplainSchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 解析 JSON 围栏内的结构化输出。
     */
    @Test
    @SuppressWarnings("unchecked")
    void parseJsonFence() {
        String raw = """
                ```json
                {
                  "summary": "下游超时",
                  "evidence": [
                    {"type":"span","ref":"s1","label":"order","reason":"500"}
                  ],
                  "suggestions": ["查 sca-inventory 超时配置"]
                }
                ```
                """;
        Map<String, Object> parsed = InsightAiExplainSchema.parseStructured(raw, mapper);
        assertTrue(Boolean.TRUE.equals(parsed.get("structured")));
        assertEquals("下游超时", parsed.get("summary"));
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) parsed.get("evidence");
        assertEquals(1, evidence.size());
        assertEquals("s1", evidence.get(0).get("ref"));
        List<String> suggestions = (List<String>) parsed.get("suggestions");
        assertEquals(1, suggestions.size());
    }

    /**
     * 非 JSON 降级为 summary。
     */
    @Test
    void parsePlainTextFallback() {
        Map<String, Object> parsed = InsightAiExplainSchema.parseStructured("只是一段散文结论", mapper);
        assertFalse(Boolean.TRUE.equals(parsed.get("structured")));
        assertTrue(String.valueOf(parsed.get("summary")).contains("散文"));
    }

    /**
     * buildResponse 附带 span nav 与 markdown。
     */
    @Test
    @SuppressWarnings("unchecked")
    void buildResponseEnrichesSpanNav() {
        Map<String, Object> parsed = InsightAiExplainSchema.parseStructured(
                "{\"summary\":\"慢\",\"evidence\":[{\"type\":\"span\",\"ref\":\"abc\",\"label\":\"x\",\"reason\":\"y\"}],\"suggestions\":[\"查库\"]}",
                mapper);
        Map<String, Object> body = InsightAiExplainSchema.buildResponse(
                "trace", false, null, "m", "p", parsed, Map.of("traceId", "t1"));
        assertEquals(1, body.get("schemaVersion"));
        assertEquals("慢", body.get("summary"));
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) body.get("evidence");
        Map<String, Object> nav = (Map<String, Object>) evidence.get(0).get("nav");
        assertNotNull(nav);
        assertEquals("span", nav.get("kind"));
        assertEquals("abc", nav.get("spanId"));
        assertTrue(String.valueOf(body.get("markdown")).contains("结论"));
        assertTrue(String.valueOf(body.get("markdown")).contains("AI 建议"));
    }

    /**
     * extractJsonObject 取首尾花括号。
     */
    @Test
    void extractJsonObject() {
        String json = InsightAiExplainSchema.extractJsonObject("前言 {\"a\":1} 后记");
        assertEquals("{\"a\":1}", json);
    }
}
