package io.github.iweidujiang.springinsight.agent.instrumentation;

/**
 * 已废弃：旧 JDBC Aspect（对 {@code java.sql.*} 的 Spring AOP 往往无效）。
 * <p>
 * 请改用 {@code spring.insight.jdbc-tracing-enabled=true}，由
 * {@link InsightTracingDataSource} 包装 DataSource。
 * </p>
 *
 * @deprecated 自 Agent 0.5 起由 DataSource 包装替代；本类不再注册为 Bean
 */
@Deprecated(since = "0.5.0", forRemoval = true)
public class DbCallAspect {
    // 保留空壳，避免外部反射引用直接 ClassNotFound；行为已迁移
}
