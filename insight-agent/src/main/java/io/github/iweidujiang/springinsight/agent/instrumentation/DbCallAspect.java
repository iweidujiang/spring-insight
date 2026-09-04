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
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库调用切面
 * 使用Spring AOP拦截JDBC操作，收集数据库调用信息
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DbCallAspect {

    private static final Pattern SQL_TYPE_PATTERN = Pattern.compile("^(SELECT|INSERT|UPDATE|DELETE|EXECUTE|CALL|MERGE|CREATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|COMMIT|ROLLBACK|SAVEPOINT|LOCK|UNLOCK)\s", Pattern.CASE_INSENSITIVE);
    
    private final SpanReportingListener spanReportingListener;
    private final String serviceName;
    private final String serviceInstance;
    private final String hostIp;
    private final Integer hostPort;

    public DbCallAspect(SpanReportingListener spanReportingListener, String serviceName, String serviceInstance, String hostIp, Integer hostPort) {
        this.spanReportingListener = spanReportingListener;
        this.serviceName = serviceName;
        this.serviceInstance = serviceInstance;
        this.hostIp = hostIp;
        this.hostPort = hostPort;
        log.info("[数据库调用切面] 初始化完成，服务名称: {}, 服务实例: {}", serviceName, serviceInstance);
    }

    /**
     * 定义Pointcut，拦截所有JDBC Statement和PreparedStatement的执行方法
     */
    @Pointcut("execution(* java.sql.Statement.execute*(String)) || execution(* java.sql.PreparedStatement.execute*())")
    public void jdbcExecution() {
    }

    /**
     * 环绕通知，拦截JDBC执行
     */
    @Around("jdbcExecution()")
    public Object aroundJdbcExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = null;
        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        
        TraceSpan span = null;
        
        try {
            // 获取SQL语句
            String sql = getSql(joinPoint);
            if (sql != null) {
                // 创建Span
                span = createDbSpan(sql);
            }
            
            // 执行JDBC操作
            result = joinPoint.proceed();
            
            return result;
        } catch (Throwable t) {
            success = false;
            errorMessage = t.getMessage();
            throw t;
        } finally {
            long endTime = System.currentTimeMillis();
            long durationMs = endTime - startTime;
            
            // 完成Span
            if (span != null) {
                span.setEndTime(endTime);
                span.setDurationMs(durationMs);
                if (!success) {
                    span.setStatusCode("ERROR");
                    span.setErrorMessage(errorMessage);
                } else {
                    span.setStatusCode("OK");
                }
                
                // 报告Span
                spanReportingListener.reportSpan(span);
            }
        }
    }
    
    /**
     * 从JoinPoint中获取SQL语句
     */
    private String getSql(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        Object target = joinPoint.getTarget();
        
        // 如果是Statement.execute*(String sql)方法
        if (args.length > 0 && args[0] instanceof String) {
            return (String) args[0];
        }
        
        // 如果是PreparedStatement.execute*()方法，尝试从PreparedStatement中获取SQL
        if (target instanceof PreparedStatement) {
            return getSqlFromPreparedStatement((PreparedStatement) target);
        }
        
        return null;
    }
    
    /**
     * 从PreparedStatement中获取SQL语句
     * 注意：这是一个近似实现，实际生产环境可能需要更复杂的方式
     */
    private String getSqlFromPreparedStatement(PreparedStatement preparedStatement) {
        try {
            // 使用反射获取SQL
            java.lang.reflect.Method getSqlMethod = preparedStatement.getClass().getMethod("getSql");
            return (String) getSqlMethod.invoke(preparedStatement);
        } catch (Exception e) {
            // 如果反射失败，尝试获取连接信息
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
    
    /**
     * 创建数据库调用Span
     */
    private TraceSpan createDbSpan(String sql) {
        String sqlType = getSqlType(sql);
        
        // 创建Span
        TraceSpan span = TraceContext.startSpan("DB " + sqlType);
        span.setSpanKind("CLIENT");
        span.setComponent("JDBC");
        
        // 设置Span属性
        span.setServiceName(serviceName);
        span.setServiceInstance(serviceInstance);
        span.setHostIp(hostIp);
        span.setHostPort(hostPort);
        span.setStartTime(System.currentTimeMillis());
        
        // 设置数据库相关标签
        span.addTag("db.sql", sql)
           .addTag("db.sql_type", sqlType);
        
        return span;
    }
    
    /**
     * 获取SQL类型
     */
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