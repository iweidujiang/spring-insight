package io.github.iweidujiang.springinsight.agent.autoconfigure;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight 自动配置导入选择器
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/17
 * └───────────────────────────────────────────────
 */
public class InsightAutoConfigurationImportSelector implements ImportSelector {
    
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        // 获取@EnableSpringInsight注解的属性
        Map<String, Object> annotationAttributes = importingClassMetadata.getAnnotationAttributes(EnableSpringInsight.class.getName());
        
        // 检查是否启用Spring Insight
        boolean enabled = (Boolean) annotationAttributes.getOrDefault("enabled", true);
        if (!enabled) {
            return new String[0];
        }
        
        // 返回需要导入的自动配置类
        return new String[] {
            InsightProperties.class.getName(),
            InsightJvmMetricsProperties.class.getName(),
            InsightBeanConfiguration.class.getName(),
            InsightAutoConfiguration.class.getName(),
            "io.github.iweidujiang.springinsight.config.SpringInsightAutoConfiguration"
        };
    }
}