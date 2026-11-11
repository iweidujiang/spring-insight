package io.github.iweidujiang.springinsight.storage;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * SQL 指纹启发式归一化：字面量改为 {@code ?}，压缩空白，便于按语句形态聚合。
 *
 * @author 苏渡苇
 * @since 2026-11-11
 */
public final class SqlFingerprintNormalizer {

    private static final int MAX_LEN = 512;

    private static final Pattern STRING_LITERAL = Pattern.compile(
            "'(?:[^']|'')*'|\"(?:[^\"]|\"\")*\"", Pattern.DOTALL);
    private static final Pattern NUMBER_LITERAL = Pattern.compile(
            "(?<![\\w.$])(?:0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)(?![\\w.$])");
    private static final Pattern IN_LIST = Pattern.compile(
            "(?i)\\(\\s*\\?(?:\\s*,\\s*\\?)+\\s*\\)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern TRAILING_ELLIPSIS = Pattern.compile("…+\\s*$");

    private SqlFingerprintNormalizer() {
    }

    /**
     * 归一化 SQL 文本为指纹；空白或无法识别时返回空串。
     *
     * @param sql 原始或截断后的 SQL
     * @return 指纹；无有效内容时 {@code ""}
     */
    public static String normalize(String sql) {
        if (sql == null) {
            return "";
        }
        String s = sql.trim();
        if (s.isEmpty()) {
            return "";
        }
        s = TRAILING_ELLIPSIS.matcher(s).replaceAll("");
        s = STRING_LITERAL.matcher(s).replaceAll("?");
        s = NUMBER_LITERAL.matcher(s).replaceAll("?");
        s = IN_LIST.matcher(s).replaceAll("(?)");
        s = WHITESPACE.matcher(s).replaceAll(" ").trim();
        if (s.length() > MAX_LEN) {
            s = s.substring(0, MAX_LEN - 1) + "…";
        }
        return s;
    }

    /**
     * 从 SQL 文本推断类型关键字（与 Agent 侧启发式类似）。
     *
     * @param sql 原始 SQL 或指纹
     * @return SELECT / INSERT / …；未知为空串
     */
    public static String sqlType(String sql) {
        if (sql == null || sql.isBlank()) {
            return "";
        }
        String head = sql.trim();
        int sp = head.indexOf(' ');
        String word = (sp < 0 ? head : head.substring(0, sp)).toUpperCase(Locale.ROOT);
        return switch (word) {
            case "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "WITH",
                    "CALL", "EXEC", "EXECUTE", "CREATE", "ALTER", "DROP", "TRUNCATE" -> word;
            default -> "";
        };
    }
}
