package io.github.iweidujiang.springinsight.agent.starter;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightAgentAutoConfigurationImportSelector;
import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * 业务侧 Agent Starter 入口自动配置。
 * <p>
 * 通过 {@link InsightAgentAutoConfigurationImportSelector} 按 Web 栈导入埋点组件；
 * 不引入 Collector、Storage、UI。上报目标由 {@code spring.insight.server-url} 决定
 * （见 {@code HttpInsightBatchSink}）。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/8/16
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(InsightProperties.class)
@ConditionalOnProperty(prefix = "spring.insight", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(InsightAgentAutoConfigurationImportSelector.class)
public class InsightAgentAutoConfiguration {

    /**
     * 构造时打印一次启用日志，便于确认 Agent Starter 已加载。
     */
    public InsightAgentAutoConfiguration() {
        log.info("[AgentStarter] Spring Insight Agent 已启用（无内嵌 UI；配置 spring.insight.server-url 上报到 insight-server；serviceName 默认可取 spring.application.name）");
    }
}
