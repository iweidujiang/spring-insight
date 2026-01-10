package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 HTTP请求追踪拦截器，拦截所有Spring MVC请求，自动创建和追踪Span
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Slf4j
public class HttpRequestInterceptor implements HandlerInterceptor {
    // 请求属性名常量
    private static final String TRACE_START_TIME_ATTR = "X-Trace-Start-Time";
    private static final String TRACE_SPAN_ATTR = "X-Trace-Span";

    private final SpanReportingListener spanReportingListener;

    public HttpRequestInterceptor(SpanReportingListener spanReportingListener) {
        this.spanReportingListener = spanReportingListener;
    }

    /**
     * 请求处理前执行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        log.info("[调试] HTTP拦截器触发: {} {}", request.getMethod(), request.getRequestURI());
        long startTime = System.currentTimeMillis();

        // 构建操作名称：方法 + 路径
        String operationName = request.getMethod() + " " + request.getRequestURI();

        // 创建并启动Span
        TraceSpan span = TraceContext.startSpan(operationName);

        // 设置Span属性
        span.setSpanKind("SERVER");
        span.setComponent("SpringMVC");

        // 添加HTTP相关标签
        span.addTag("http.method", request.getMethod())
                .addTag("http.path", request.getRequestURI())
                .addTag("http.query", request.getQueryString())
                .addTag("http.client_ip", getClientIp(request))
                .addTag("http.user_agent", request.getHeader("User-Agent"));

        // 将开始时间和Span存储到请求属性中，供后续使用
        request.setAttribute(TRACE_START_TIME_ATTR, startTime);
        request.setAttribute(TRACE_SPAN_ATTR, span);

        log.debug("[HTTP拦截器] 开始追踪请求: traceId={}, spanId={}, operation={}, uri={}",
                span.getTraceId(), span.getSpanId(), operationName, request.getRequestURI());

        return true; // 继续处理请求
    }

    /**
     * 请求处理后执行（渲染视图前）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {

        TraceSpan span = (TraceSpan) request.getAttribute(TRACE_SPAN_ATTR);
        if (span == null) {
            log.warn("[HTTP拦截器] 请求完成，但未找到对应的TraceSpan: uri={}", request.getRequestURI());
            return;
        }

        // 根据异常和状态码判断请求是否成功
        String errorCode = null;
        String errorMessage = null;

        if (ex != null) {
            // 有异常，请求失败
            errorCode = "EXCEPTION";
            errorMessage = ex.getClass().getName() + ": " + ex.getMessage();
            log.debug("[HTTP拦截器] 请求处理异常: traceId={}, error={}", span.getTraceId(), errorMessage);
        } else if (response.getStatus() >= 400) {
            // HTTP状态码表示错误
            errorCode = "HTTP_" + response.getStatus();
            errorMessage = "HTTP Status: " + response.getStatus();
        }

        // 添加响应相关标签
        span.addTag("http.status_code", String.valueOf(response.getStatus()))
                .addTag("http.response_size", String.valueOf(response.getBufferSize()));

        // 结束Span
        Optional<TraceSpan> endedSpan = TraceContext.endSpan(errorCode, errorMessage);

        // 将结束的Span报告给监听器
        endedSpan.ifPresent(s -> {
            log.debug("[HTTP拦截器] 准备上报已结束的Span: {}", s.getSpanId());
            reportSpanToListener(s);
        });

        // 记录请求完成日志
        log.debug("[HTTP拦截器] 请求完成: traceId={}, spanId={}, uri={}, status={}, duration={}ms",
                span.getTraceId(), span.getSpanId(), request.getRequestURI(),
                response.getStatus(), span.getDurationMs());

        // 清理当前线程的追踪上下文（防止内存泄漏）
        TraceContext.clear();
    }

    // 新增私有方法
    private void reportSpanToListener(TraceSpan span) {
        if (spanReportingListener != null) {
            spanReportingListener.reportSpan(span);
            log.debug("[HTTP拦截器] Span已提交给上报监听器: spanId={}", span.getSpanId());
        } else {
            log.warn("[HTTP拦截器] 无法上报Span，上报监听器未初始化");
        }
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 多个IP时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}
