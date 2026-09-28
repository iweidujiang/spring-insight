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
     * 推理草稿不直接展示为结论。
     */
    @Test
    void parseReasoningDraftNotShownAsSummary() {
        String raw = "We need answer only JSON. Need infer. Provided edge sca-user -> sca-loyalty, hours 6, call_count 2, avg_duration 93.5.";
        Map<String, Object> parsed = InsightAiExplainSchema.parseStructured(raw, mapper);
        assertFalse(Boolean.TRUE.equals(parsed.get("structured")));
        assertFalse(InsightAiExplainSchema.isStructuredOk(parsed));
        String summary = String.valueOf(parsed.get("summary"));
        assertTrue(summary.contains("结构化结论") || summary.contains("思考"));
        assertFalse(summary.contains("We need"));
    }

    /**
     * 思考草稿末尾若带 summary JSON，应抽出结构化结果。
     */
    @Test
    void parseJsonEmbeddedAfterReasoning() {
        String raw = """
                We need answer only JSON. Need infer.
                {"summary":"调用偏少但耗时正常","evidence":[{"type":"edge","ref":"sca-user->sca-loyalty","label":"user→loyalty","reason":"2次"}],"suggestions":["关注错误率"]}
                """;
        Map<String, Object> parsed = InsightAiExplainSchema.parseStructured(raw, mapper);
        assertTrue(Boolean.TRUE.equals(parsed.get("structured")));
        assertEquals("调用偏少但耗时正常", parsed.get("summary"));
    }

    /**
     * ```json 与 { 同行、且 JSON 截断时，仍只展示 summary 一句。
     */
    @Test
    void parseTruncatedJsonFenceShowsSummaryOnly() {
        String raw = "```json { \"summary\": \"sca-user 到 sca-loyalty 在 6 小时内仅 2 次调用、平均耗时 93.5，样本过少，暂不能判定异常，需结合基线继续观察。\", \"evidence\": [ {";
        Map<String, Object> parsed = InsightAiExplainSchema.parseStructured(raw, mapper);
        assertTrue(Boolean.TRUE.equals(parsed.get("structured")));
        assertEquals(
                "sca-user 到 sca-loyalty 在 6 小时内仅 2 次调用、平均耗时 93.5，样本过少，暂不能判定异常，需结合基线继续观察。",
                parsed.get("summary"));
        assertFalse(String.valueOf(parsed.get("summary")).contains("evidence"));
        assertFalse(String.valueOf(parsed.get("summary")).contains("```"));
    }

    /**
     * 完整围栏 JSON（无换行）也能解析。
     */
    @Test
    void parseJsonFenceSameLine() {
        String raw = "```json {\"summary\":\"下游偏慢\",\"evidence\":[],\"suggestions\":[\"查超时\"]} ```";
        Map<String, Object> parsed = InsightAiExplainSchema.parseStructured(raw, mapper);
        assertTrue(Boolean.TRUE.equals(parsed.get("structured")));
        assertEquals("下游偏慢", parsed.get("summary"));
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
     * errors 证据跳转错误分析页。
     */
    @Test
    @SuppressWarnings("unchecked")
    void buildResponseEnrichesErrorsNav() {
        Map<String, Object> parsed = InsightAiExplainSchema.parseStructured(
                "{\"summary\":\"错误上升\",\"evidence\":[{\"type\":\"errors\",\"ref\":\"\",\"label\":\"错误分析\",\"reason\":\"环比+\"}],\"suggestions\":[]}",
                mapper);
        Map<String, Object> body = InsightAiExplainSchema.buildResponse(
                "period", false, null, "m", "p", parsed, Map.of("hours", 12));
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) body.get("evidence");
        Map<String, Object> nav = (Map<String, Object>) evidence.get(0).get("nav");
        assertNotNull(nav);
        assertEquals("error-analysis", nav.get("kind"));
        assertEquals(12, nav.get("hours"));
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
