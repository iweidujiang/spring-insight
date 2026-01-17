package io.github.iweidujiang.springinsight.agent.autoconfigure;

import io.github.iweidujiang.springinsight.agent.collector.AsyncSpanReporter;
import io.github.iweidujiang.springinsight.agent.collector.JvmMetricsCollector;
import io.github.iweidujiang.springinsight.agent.collector.JvmMetricsReporter;
import io.github.iweidujiang.springinsight.agent.instrumentation.DbCallAspect;
import io.github.iweidujiang.springinsight.agent.instrumentation.HttpRequestInterceptor;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 @EnableSpringInsight 注解测试
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/17
 * └───────────────────────────────────────────────
 */
@SpringBootTest(classes = EnableSpringInsightTest.TestApplication.class)
@EnableSpringInsight(
        serviceName = "test-service",
        sampleRate = 0.8,
        httpTracingEnabled = true,
        jvmMetricsEnabled = true,
        dbMetricsEnabled = true,
        collectorUrl = "http://localhost:8080"
)
public class EnableSpringInsightTest {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private InsightProperties insightProperties;
    
    @Test
    void testAnnotationPropertiesMerged() {
        // 验证注解属性是否被正确合并
        assertThat(insightProperties.getServiceName()).isEqualTo("test-service");
        assertThat(insightProperties.getSampleRate()).isEqualTo(0.8);
        assertThat(insightProperties.isHttpTracingEnabled()).isTrue();
        assertThat(insightProperties.getCollector().getUrl()).isEqualTo("http://localhost:8080");
    }
    
    @Test
    void testBeansCreated() {
        // 验证核心bean是否被正确创建
        assertThat(applicationContext.containsBean("asyncSpanReporter")).isTrue();
        assertThat(applicationContext.containsBean("spanReportingListener")).isTrue();
        assertThat(applicationContext.containsBean("httpRequestInterceptor")).isTrue();
        assertThat(applicationContext.containsBean("jvmMetricsCollector")).isTrue();
        assertThat(applicationContext.containsBean("jvmMetricsReporter")).isTrue();
        assertThat(applicationContext.containsBean("dbCallAspect")).isTrue();
    }
    
    @Test
    void testAsyncSpanReporterConfigured() {
        // 验证AsyncSpanReporter是否被正确配置
        AsyncSpanReporter reporter = applicationContext.getBean(AsyncSpanReporter.class);
        assertThat(reporter).isNotNull();
    }
    
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @EnableSpringInsight(enabled = false)
    static class DisableTest {
        
        @Autowired(required = false)
        private AsyncSpanReporter asyncSpanReporter;
        
        @Test
        void testDisabled() {
            // 验证禁用@EnableSpringInsight后，相关bean不会被创建
            assertThat(asyncSpanReporter).isNull();
        }
    }
    
    @SpringBootTest
    @EnableSpringInsight
    static class TestApplication {
        // 空应用类，用于测试
    }
}