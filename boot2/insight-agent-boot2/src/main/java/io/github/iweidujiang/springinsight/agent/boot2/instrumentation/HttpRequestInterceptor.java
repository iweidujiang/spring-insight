/**
 * HttpRequestInterceptor?Spring MVC ?? SERVER Span?javax.servlet??
 *
 * @since?2026-09-07
 * @author???? ???????
 *
 * GitHub?https://github.com/iweidujiang
 */
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

public class HttpRequestInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestInterceptor.class);

    /** ??????? SERVER Span */
    private static final String TRACE_SPAN_ATTR = "X-Insight-Boot2-Span";

    private final SpanReportingListener spanReportingListener;
    private final InsightBoot2Properties insightProperties;

    /**
     * @param spanReportingListener ????
     * @param insightProperties     ??
     */
    public HttpRequestInterceptor(SpanReportingListener spanReportingListener,
                                  InsightBoot2Properties insightProperties) {
        this.spanReportingListener = spanReportingListener;
        this.insightProperties = insightProperties;
    }

    /**
     * ??????? SERVER Span ??? TraceContext?
     *
     * @param request  ??
     * @param response ??
     * @param handler  ???
     * @return ?? true?????
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
            log.info("[HTTP??-Boot2] ??: traceId={}, {}", span.getTraceId(), operationName);
        }
        return true;
    }

    /**
     * ??????????????????
     *
     * @param request      ??
     * @param response     ??
     * @param handler      ???
     * @param modelAndView ????
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        // ????????? afterCompletion
    }

    /**
     * ??????? Span?????? ThreadLocal?
     *
     * @param request  ??
     * @param response ??
     * @param handler  ???
     * @param ex       ???????? null
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
     * ????? IP??? X-Forwarded-For??
     *
     * @param request ??
     * @return IP ?????????
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
