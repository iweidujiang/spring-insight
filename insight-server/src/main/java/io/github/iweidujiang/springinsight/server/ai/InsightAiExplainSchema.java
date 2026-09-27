package io.github.iweidujiang.springinsight.server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 解读统一结果（schemaVersion=1）：摘要、可点证据、处置建议；并保留 markdown 兼容旧 UI。
 *
 * @since 2026-09-24
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public final class InsightAiExplainSchema {

    /** 当前结果结构版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 免责声明尾注。 */
    public static final String DISCLAIMER = "\n\n---\n*AI 建议，请以 Span 为准。*";

    /** 要求模型只输出 JSON 的公共说明。 */
    public static final String JSON_OUTPUT_RULES = """
            你必须只输出一个 JSON 对象（可包在 ```json 代码块中），不要输出其它说明文字。
            若模型支持「思考 / reasoning」，最终答案必须写在正式回复正文（content）里，不要只写在思考过程中。
            字段：
            {
              "summary": "一句话结论",
              "evidence": [
                {"type":"span|service|status|exception|edge|trace","ref":"引用值","label":"短标签","reason":"为何可疑"}
              ],
              "suggestions": ["可执行排查建议，最多 5 条"]
            }
            规则：
            1. 只依据用户提供的 JSON，勿编造未出现的服务名、状态码、异常或 Span。
            2. evidence.type 含义：span=spanId；service=服务名；status=HTTP 状态码键；exception=异常类；edge=源服务->目标服务；trace=traceId。
            3. evidence 最多 5 条；suggestions 最多 5 条；summary 不超过 80 字。
            """;

    private InsightAiExplainSchema() {
    }

    /**
     * 将模型原文解析为结构化字段；解析失败时不把推理草稿当结论。
     *
     * @param rawModelText 模型返回文本
     * @param mapper       Jackson
     * @return summary / evidence / suggestions / structured / rawText
     */
    public static Map<String, Object> parseStructured(String rawModelText, ObjectMapper mapper) {
        Map<String, Object> out = new LinkedHashMap<>();
        String raw = rawModelText != null ? rawModelText.trim() : "";
        out.put("rawText", raw);
        String json = extractJsonObject(raw);
        if (!StringUtils.hasText(json)) {
            out.put("structured", false);
            out.put("summary", fallbackSummary(raw));
            out.put("evidence", List.of());
            out.put("suggestions", List.of());
            return out;
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (!looksLikeExplainObject(root)) {
                out.put("structured", false);
                out.put("summary", fallbackSummary(raw));
                out.put("evidence", List.of());
                out.put("suggestions", List.of());
                return out;
            }
            String summary = textOrEmpty(root.get("summary"));
            if (!StringUtils.hasText(summary)) {
                summary = fallbackSummary(raw);
            }
            List<Map<String, Object>> evidence = parseEvidence(root.get("evidence"));
            List<String> suggestions = parseSuggestions(root.get("suggestions"));
            out.put("structured", true);
            out.put("summary", summary.trim());
            out.put("evidence", evidence);
            out.put("suggestions", suggestions);
            return out;
        } catch (Exception e) {
            out.put("structured", false);
            out.put("summary", fallbackSummary(raw));
            out.put("evidence", List.of());
            out.put("suggestions", List.of());
            return out;
        }
    }

    /**
     * 是否已得到可用的结构化结论（供调用方决定是否降级）。
     *
     * @param parsed {@link #parseStructured} 结果
     * @return true 表示有 summary 且 structured
     */
    public static boolean isStructuredOk(Map<String, Object> parsed) {
        return parsed != null
                && Boolean.TRUE.equals(parsed.get("structured"))
                && StringUtils.hasText(String.valueOf(parsed.getOrDefault("summary", "")));
    }

    /**
     * 组装统一 API 响应（含 markdown 兼容字段）。
     *
     * @param kind       trace | errors | dependency
     * @param degraded   是否降级
     * @param message    降级或提示文案；成功时可为空
     * @param model      模型名
     * @param provider   提供方
     * @param parsed     {@link #parseStructured} 结果；降级时可传 null
     * @param navContext 用于给 evidence 补 nav（hours / traceId / source / target）
     * @return 响应 Map
     */
    public static Map<String, Object> buildResponse(String kind,
                                                    boolean degraded,
                                                    String message,
                                                    String model,
                                                    String provider,
                                                    Map<String, Object> parsed,
                                                    Map<String, Object> navContext) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", SCHEMA_VERSION);
        body.put("kind", kind != null ? kind : "");
        body.put("degraded", degraded);
        if (StringUtils.hasText(message)) {
            body.put("message", message);
        }
        body.put("model", model != null ? model : "");
        body.put("provider", provider != null ? provider : "");

        String summary;
        List<Map<String, Object>> evidence;
        List<String> suggestions;
        boolean structured;
        if (parsed != null) {
            summary = String.valueOf(parsed.getOrDefault("summary", ""));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ev = (List<Map<String, Object>>) parsed.getOrDefault("evidence", List.of());
            @SuppressWarnings("unchecked")
            List<String> sug = (List<String>) parsed.getOrDefault("suggestions", List.of());
            evidence = enrichEvidenceNav(ev, navContext);
            suggestions = sug;
            structured = Boolean.TRUE.equals(parsed.get("structured"));
        } else {
            summary = StringUtils.hasText(message) ? message : "未能自动解释";
            evidence = List.of();
            suggestions = List.of();
            structured = false;
        }
        body.put("structured", structured);
        body.put("summary", summary);
        body.put("evidence", evidence);
        body.put("suggestions", suggestions);
        body.put("markdown", renderMarkdown(summary, evidence, suggestions) + DISCLAIMER);

        if (navContext != null) {
            copyIfPresent(body, navContext, "traceId");
            copyIfPresent(body, navContext, "hours");
            copyIfPresent(body, navContext, "source");
            copyIfPresent(body, navContext, "target");
        }
        return body;
    }

    /**
     * 由结构化字段生成兼容用 Markdown。
     *
     * @param summary     摘要
     * @param evidence    证据
     * @param suggestions 建议
     * @return Markdown（不含免责声明）
     */
    public static String renderMarkdown(String summary,
                                        List<Map<String, Object>> evidence,
                                        List<String> suggestions) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 结论\n\n");
        sb.append(StringUtils.hasText(summary) ? summary.trim() : "（无）");
        sb.append("\n\n");
        if (evidence != null && !evidence.isEmpty()) {
            sb.append("### 可疑证据\n\n");
            for (Map<String, Object> item : evidence) {
                String label = String.valueOf(item.getOrDefault("label", item.getOrDefault("ref", "")));
                String reason = String.valueOf(item.getOrDefault("reason", ""));
                sb.append("- **").append(label).append("**");
                if (StringUtils.hasText(reason) && !"null".equals(reason)) {
                    sb.append("：").append(reason);
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
        if (suggestions != null && !suggestions.isEmpty()) {
            sb.append("### 建议\n\n");
            int i = 1;
            for (String s : suggestions) {
                if (!StringUtils.hasText(s)) {
                    continue;
                }
                sb.append(i++).append(". ").append(s.trim()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /**
     * 为证据项补充前端可跳转的 {@code nav}。
     *
     * @param evidence   原始证据
     * @param navContext hours / traceId / source / target
     * @return 新列表
     */
    static List<Map<String, Object>> enrichEvidenceNav(List<Map<String, Object>> evidence,
                                                       Map<String, Object> navContext) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        Integer hours = null;
        String defaultTraceId = null;
        if (navContext != null) {
            Object h = navContext.get("hours");
            if (h instanceof Number n) {
                hours = n.intValue();
            }
            Object t = navContext.get("traceId");
            if (t != null && StringUtils.hasText(String.valueOf(t))) {
                defaultTraceId = String.valueOf(t);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>(evidence.size());
        for (Map<String, Object> raw : evidence) {
            Map<String, Object> item = new LinkedHashMap<>(raw);
            String type = String.valueOf(item.getOrDefault("type", "")).toLowerCase(Locale.ROOT);
            String ref = String.valueOf(item.getOrDefault("ref", "")).trim();
            Map<String, Object> nav = buildNav(type, ref, defaultTraceId, hours, navContext);
            if (nav != null) {
                item.put("nav", nav);
            }
            out.add(item);
        }
        return out;
    }

    private static Map<String, Object> buildNav(String type,
                                                String ref,
                                                String defaultTraceId,
                                                Integer hours,
                                                Map<String, Object> navContext) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        Map<String, Object> nav = new LinkedHashMap<>();
        switch (type) {
            case "span" -> {
                if (!StringUtils.hasText(ref) || !StringUtils.hasText(defaultTraceId)) {
                    return null;
                }
                nav.put("kind", "span");
                nav.put("traceId", defaultTraceId);
                nav.put("spanId", ref);
            }
            case "trace" -> {
                String tid = StringUtils.hasText(ref) ? ref : defaultTraceId;
                if (!StringUtils.hasText(tid)) {
                    return null;
                }
                nav.put("kind", "trace");
                nav.put("traceId", tid);
            }
            case "service" -> {
                if (!StringUtils.hasText(ref)) {
                    return null;
                }
                nav.put("kind", "traces");
                nav.put("service", ref);
                if (hours != null) {
                    nav.put("hours", hours);
                }
                nav.put("status", "error");
            }
            case "status", "exception" -> {
                if (!StringUtils.hasText(ref)) {
                    return null;
                }
                nav.put("kind", "traces");
                nav.put("q", ref);
                nav.put("status", "error");
                if (hours != null) {
                    nav.put("hours", hours);
                }
            }
            case "edge" -> {
                String source = null;
                String target = null;
                if (ref.contains("->")) {
                    String[] parts = ref.split("->", 2);
                    source = parts[0].trim();
                    target = parts[1].trim();
                } else if (navContext != null) {
                    source = str(navContext.get("source"));
                    target = str(navContext.get("target"));
                }
                if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
                    return null;
                }
                nav.put("kind", "topology");
                nav.put("source", source);
                nav.put("target", target);
                if (hours != null) {
                    nav.put("hours", hours);
                }
            }
            default -> {
                return null;
            }
        }
        return nav;
    }

    private static List<Map<String, Object>> parseEvidence(JsonNode node) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return list;
        }
        int n = 0;
        for (JsonNode item : node) {
            if (n >= 5 || item == null || !item.isObject()) {
                continue;
            }
            String type = textOrEmpty(item.get("type")).toLowerCase(Locale.ROOT);
            String ref = textOrEmpty(item.get("ref"));
            if (!StringUtils.hasText(ref) && item.has("id")) {
                ref = textOrEmpty(item.get("id"));
            }
            String label = textOrEmpty(item.get("label"));
            if (!StringUtils.hasText(label)) {
                label = ref;
            }
            String reason = textOrEmpty(item.get("reason"));
            if (!StringUtils.hasText(reason) && item.has("hint")) {
                reason = textOrEmpty(item.get("hint"));
            }
            if (!StringUtils.hasText(type) && !StringUtils.hasText(ref)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", type);
            row.put("ref", ref);
            row.put("label", label);
            row.put("reason", reason);
            list.add(row);
            n++;
        }
        return list;
    }

    private static List<String> parseSuggestions(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return list;
        }
        for (JsonNode item : node) {
            if (list.size() >= 5) {
                break;
            }
            if (item != null && item.isTextual()) {
                String t = item.asText().trim();
                if (StringUtils.hasText(t)) {
                    list.add(t);
                }
            }
        }
        return list;
    }

    /**
     * 从模型输出中抽出含 summary 的 JSON 对象（支持 ```json 围栏；跳过思考草稿里的无关花括号）。
     *
     * @param raw 原文
     * @return JSON 或空
     */
    static String extractJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String t = raw.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                t = t.substring(firstNl + 1, lastFence).trim();
                if (t.regionMatches(true, 0, "json", 0, 4)) {
                    t = t.substring(4).trim();
                }
            }
        }
        // 优先：能解析且含 summary 的对象（避免把思考过程里的 {call_count:2} 当成结论）
        String withSummary = findBalancedObjectContaining(t, "summary");
        if (StringUtils.hasText(withSummary)) {
            return withSummary;
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return t.substring(start, end + 1);
    }

    /**
     * 扫描文本，返回第一个括号平衡且包含指定字段名的 JSON 对象。
     *
     * @param text      原文
     * @param fieldName 必含字段（如 summary）
     * @return JSON 片段或空
     */
    static String findBalancedObjectContaining(String text, String fieldName) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(fieldName)) {
            return "";
        }
        String needle = "\"" + fieldName + "\"";
        int from = 0;
        while (from < text.length()) {
            int start = text.indexOf('{', from);
            if (start < 0) {
                return "";
            }
            int end = findMatchingBrace(text, start);
            if (end < 0) {
                from = start + 1;
                continue;
            }
            String candidate = text.substring(start, end + 1);
            if (candidate.contains(needle)) {
                return candidate;
            }
            from = start + 1;
        }
        return "";
    }

    /**
     * @param text  全文
     * @param start '{' 下标
     * @return 配对 '}' 下标；失败 -1
     */
    private static int findMatchingBrace(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean looksLikeExplainObject(JsonNode root) {
        return root != null && root.isObject() && root.has("summary");
    }

    private static String fallbackSummary(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "未能解析模型输出";
        }
        // 推理模型常把草稿放进 content/reasoning；勿直接展示给用户
        if (looksLikeReasoningDraft(raw)) {
            return "模型未输出结构化结论（仅有思考草稿）。可换非推理模型，或在设置中增大 max_tokens 后重试。";
        }
        String one = raw.replace('\r', ' ').replace('\n', ' ').trim();
        return one.length() <= 120 ? one : one.substring(0, 120) + "...";
    }

    /**
     * 粗判：像英文 CoT /「只输出 JSON」自检，而非给用户的结论。
     *
     * @param raw 原文
     * @return true 表示不宜直接展示
     */
    static boolean looksLikeReasoningDraft(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("we need") && lower.contains("json")) {
            return true;
        }
        if (lower.contains("answer only json") || lower.contains("only output json")) {
            return true;
        }
        if (lower.contains("need infer") || lower.contains("let's think")) {
            return true;
        }
        // 中文思考草稿常见开头
        return raw.contains("我需要") && (raw.contains("JSON") || raw.contains("json"));
    }

    private static String textOrEmpty(JsonNode n) {
        if (n == null || n.isNull() || !n.isTextual()) {
            return n != null && n.isNumber() ? n.asText() : "";
        }
        return n.asText().trim();
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> src, String key) {
        if (src.containsKey(key) && src.get(key) != null) {
            target.put(key, src.get(key));
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
