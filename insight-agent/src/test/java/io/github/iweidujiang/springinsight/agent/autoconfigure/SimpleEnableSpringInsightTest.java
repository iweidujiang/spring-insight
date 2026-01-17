package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.InsightAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 简单的@EnableSpringInsight注解测试
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/17
 * └───────────────────────────────────────────────
 */
@SpringBootTest(classes = InsightAgentApplication.class)
public class SimpleEnableSpringInsightTest {
    
    @Test
    void testApplicationStarts() {
        // 简单测试，验证应用能够正常启动
        // 主要用于验证@EnableSpringInsight注解没有导致应用启动失败
        assert true;
    }
}