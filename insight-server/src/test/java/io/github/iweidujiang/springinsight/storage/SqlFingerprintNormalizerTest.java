package io.github.iweidujiang.springinsight.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 指纹归一化。
 *
 * @author 苏渡苇
 * @since 2026-11-11
 */
class SqlFingerprintNormalizerTest {

    /**
     * 不同字面量归一化为同一指纹。
     */
    @Test
    void literalsCollapseToSameFingerprint() {
        String a = SqlFingerprintNormalizer.normalize(
                "SELECT * FROM users WHERE id = 1 AND name = 'alice'");
        String b = SqlFingerprintNormalizer.normalize(
                "SELECT * FROM users WHERE id = 99 AND name = 'bob'");
        assertEquals(a, b);
        assertTrue(a.contains("?"));
        assertEquals("SELECT * FROM users WHERE id = ? AND name = ?", a);
    }

    /**
     * IN 列表压成单个占位。
     */
    @Test
    void inListCollapsed() {
        String fp = SqlFingerprintNormalizer.normalize(
                "SELECT id FROM t WHERE id IN (1, 2, 3)");
        assertEquals("SELECT id FROM t WHERE id IN (?)", fp);
    }

    /**
     * 截断省略号不影响指纹主体。
     */
    @Test
    void trailingEllipsisStripped() {
        String fp = SqlFingerprintNormalizer.normalize("SELECT 1 FROM dual…");
        assertEquals("SELECT ? FROM dual", fp);
    }

    /**
     * 类型推断。
     */
    @Test
    void sqlTypeFromHead() {
        assertEquals("UPDATE", SqlFingerprintNormalizer.sqlType("update users set x=1"));
        assertEquals("", SqlFingerprintNormalizer.sqlType(""));
    }
}
