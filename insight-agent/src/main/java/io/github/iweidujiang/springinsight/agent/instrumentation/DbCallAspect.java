package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库调用切面：拦截 JDBC 执行，创建 CLIENT Span 并正确弹出 Trace 栈。
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DbCallAspect {

    private static final Pattern SQL_TYPE_PATTERN = Pattern.compile(
            "^(SELECT|INSERT|UPDATE|DELETE|EXECUTE|CALL|MERGE|CREATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|COMMIT|ROLLBACK|SAVEPOINT|LOCK|UNLOCK)\\s",
            Pattern.CASE_INSENSITIVE);

    private final SpanReportingListener spanReportingListener;
    private final String serviceName;
    private final String serviceInstance;
    private final String hostIp;
    private final Integer hostPort;

    public DbCallAspect(SpanReportingListener spanReportingListener, String serviceName,
                        String serviceInstance, String hostIp, Integer hostPort) {
        this.spanReportingListener = spanReportingListener;
        this.serviceName = serviceName;
        this.serviceInstance = serviceInstance;
        this.hostIp = hostIp;
        this.hostPort = hostPort;
        log.info("[数据库调用切面] 初始化完成，服务名称: {}, 服务实例: {}", serviceName, serviceInstance);
    }

    @Pointcut("execution(* java.sql.Statement.execute*(String)) || execution(* java.sql.PreparedStatement.execute*())")
    public void jdbcExecution() {
    }

    @Around("jdbcExecution()")
    public Object aroundJdbcExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String sql = getSql(joinPoint);
        TraceSpan span = null;
        if (sql != null) {
            span = createDbSpan(sql);
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            if (span != null) {
                TraceContext.endSpan("DB_ERROR", t.getMessage())
                        .ifPresent(finished -> spanReportingListener.reportSpan(TraceSpan.snapshot(finished)));
                span = null;
            }
            throw t;
        } finally {
            if (span != null) {
                TraceContext.endSpan()
                        .ifPresent(finished -> spanReportingListener.reportSpan(TraceSpan.snapshot(finished)));
            }
        }
    }

    private String getSql(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        Object target = joinPoint.getTarget();

        if (args.length > 0 && args[0] instanceof String) {
            return (String) args[0];
        }

        if (target instanceof PreparedStatement) {
            return getSqlFromPreparedStatement((PreparedStatement) target);
        }

        return null;
    }

    private String getSqlFromPreparedStatement(PreparedStatement preparedStatement) {
        try {
            java.lang.reflect.Method getSqlMethod = preparedStatement.getClass().getMethod("getSql");
            return (String) getSqlMethod.invoke(preparedStatement);
        } catch (Exception e) {
            try {
                Connection connection = preparedStatement.getConnection();
                String url = connection.getMetaData().getURL();
                log.debug("[数据库调用切面] 无法获取PreparedStatement的SQL，仅记录连接信息: {}", url);
                return "PreparedStatement: " + url;
            } catch (Exception ex) {
                log.warn("[数据库调用切面] 无法获取数据库连接信息: {}", ex.getMessage());
                return "PreparedStatement: unknown";
            }
        }
    }

    private TraceSpan createDbSpan(String sql) {
        String sqlType = getSqlType(sql);

        TraceSpan span = TraceContext.startSpan("DB " + sqlType);
        span.setSpanKind("CLIENT");
        span.setComponent("JDBC");

        span.setServiceName(serviceName);
        span.setServiceInstance(serviceInstance);
        span.setHostIp(hostIp);
        span.setHostPort(hostPort);

        span.addTag("db.sql", sql)
                .addTag("db.sql_type", sqlType);

        return span;
    }

    private String getSqlType(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "UNKNOWN";
        }

        Matcher matcher = SQL_TYPE_PATTERN.matcher(sql.trim());
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return "UNKNOWN";
    }
}
