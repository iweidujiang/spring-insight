package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

/**
 * HttpRequestInterceptor：Spring MVC 入站 SERVER Span（javax.servlet）。
 *
 * @since 2026-09-07
 * @author 苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
public class HttpRequestInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestInterceptor.class);

    /** Request 属性：本次 SERVER Span */
    private static final String TRACE_SPAN_ATTR = "X-Insight-Boot2-Span";

    private final SpanReportingListener spanReportingListener;
    private final InsightBoot2Properties insightProperties;

    /**
     * @param spanReportingListener 上报入口
     * @param insightProperties     配置
     */
    public HttpRequestInterceptor(SpanReportingListener spanReportingListener,
                                  InsightBoot2Properties insightProperties) {
        this.spanReportingListener = spanReportingListener;
        this.insightProperties = insightProperties;
    }

    /**
     * 请求开始：创建 SERVER Span 并压入 TraceContext。
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @return 始终 true，不拦截请求
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String operationName = request.getMethod() + " " + request.getRequestURI();
        TraceSpan span = TraceContext.startSpan(operationName);
        span.setSpanKind("SERVER");
        span.setComponent("SpringMVC");
        span.setServiceName(insightProperties.getServiceName());
        span.addTag("http.method", request.getMethod())
                .addTag("http.path", request.getRequestURI())
                .addTag("http.query", request.getQueryString() != null ? request.getQueryString() : "")
                .addTag("http.client_ip", clientIp(request));
        request.setAttribute(TRACE_SPAN_ATTR, span);
        if (insightProperties.isDiagnosticLogs()) {
            log.info("[Boot2-HTTP] 开始: traceId={}, {}", span.getTraceId(), operationName);
        }
        return true;
    }

    /**
     * 控制器执行完毕；结束逻辑放在 afterCompletion。
     *
     * @param request      请求
     * @param response     响应
     * @param handler      处理器
     * @param modelAndView 视图模型
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        // 结束逻辑统一放在 afterCompletion
    }

    /**
     * 请求结束：结束 Span、上报并清理 ThreadLocal。
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @param ex       异常，无异常时为 null
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TraceSpan span = (TraceSpan) request.getAttribute(TRACE_SPAN_ATTR);
        if (span == null) {
            TraceContext.clear();
            return;
        }
        String errorCode = null;
        String errorMessage = null;
        if (ex != null) {
            errorCode = "EXCEPTION";
            errorMessage = ex.getClass().getName() + ": " + ex.getMessage();
        } else if (response.getStatus() >= 400) {
            errorCode = "HTTP_" + response.getStatus();
            errorMessage = "HTTP Status: " + response.getStatus();
        }
        span.addTag("http.status_code", String.valueOf(response.getStatus()));
        Optional<TraceSpan> ended = TraceContext.endSpan(errorCode, errorMessage);
        if (ended.isPresent()) {
            spanReportingListener.reportSpan(ended.get());
        }
        TraceContext.clear();
    }

    /**
     * 解析客户端 IP（优先 X-Forwarded-For）。
     *
     * @param request 请求
     * @return IP 字符串，解析失败时为空串
     */
    private static String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "";
    }
}
