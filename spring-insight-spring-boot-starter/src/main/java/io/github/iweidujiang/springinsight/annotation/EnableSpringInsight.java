package io.github.iweidujiang.springinsight.annotation;

import io.github.iweidujiang.springinsight.config.SpringInsightAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 启用注解
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │ 📅 @since 2026/1/17
 * └───────────────────────────────────────────────
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(SpringInsightAutoConfiguration.class)
public @interface EnableSpringInsight {
}
