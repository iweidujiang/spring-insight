package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.context.ReactiveTraceHolder;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * WebFlux 入口 HTTP 追踪：Span 挂在 exchange 属性与 Reactor Context，避免 ThreadLocal 丢失。
 *
 * @since 2026-09-07
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ReactiveInsightWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ReactiveInsightWebFilter.class);

    /** 供出站 Filter 等读取入口 SERVER Span */
    public static final String SPAN_EXCHANGE_ATTR = ReactiveInsightWebFilter.class.getName() + ".span";

    private final SpanReportingListener spanReportingListener;
    private final InsightBoot2Properties insightProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * @param spanReportingListener 上报入口
     * @param insightProperties     配置
     */
    public ReactiveInsightWebFilter(SpanReportingListener spanReportingListener,
                                    InsightBoot2Properties insightProperties) {
        this.spanReportingListener = spanReportingListener;
        this.insightProperties = insightProperties;
    }

    /**
     * 创建 SERVER Span，写入 exchange 与 Reactor Context，请求结束时上报。
     *
     * @param exchange 当前交换
     * @param chain    Filter 链
     * @return 空 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!insightProperties.isHttpTracingEnabled()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().pathWithinApplication().value();
        String[] excludes = insightProperties.resolveExcludePatterns();
        for (int i = 0; i < excludes.length; i++) {
            if (pathMatcher.match(excludes[i], path)) {
                return chain.filter(exchange);
            }
        }

        HttpMethod httpMethod = request.getMethod();
        String method = httpMethod != null ? httpMethod.name() : "UNKNOWN";
        String operationName = method + " " + path;
        TraceSpan span = new TraceSpan();
        span.setOperationName(operationName);
        span.setSpanKind("SERVER");
        span.setComponent("SpringWebFlux");
        span.setServiceName(insightProperties.getServiceName());
        String rawQuery = request.getURI().getRawQuery();
        span.addTag("http.method", method)
                .addTag("http.path", path)
                .addTag("http.query", rawQuery != null ? rawQuery : "")
                .addTag("http.client_ip", clientIp(request))
                .addTag("http.user_agent",
                        Optional.ofNullable(request.getHeaders().getFirst(HttpHeaders.USER_AGENT)).orElse(""));

        if (insightProperties.isDiagnosticLogs()) {
            log.info("[Boot2-WebFlux] 开始: traceId={}, spanId={}, {}",
                    span.getTraceId(), span.getSpanId(), operationName);
        }

        exchange.getAttributes().put(SPAN_EXCHANGE_ATTR, span);

        return chain.filter(exchange)
                .doOnError(new java.util.function.Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable ex) {
                        finalizeSpan(exchange, ex);
                    }
                })
                .doFinally(new java.util.function.Consumer<SignalType>() {
                    @Override
                    public void accept(SignalType signal) {
                        if (signal != SignalType.ON_ERROR) {
                            finalizeSpan(exchange, null);
                        }
                    }
                })
                .contextWrite(new java.util.function.Function<reactor.util.context.Context, reactor.util.context.Context>() {
                    @Override
                    public reactor.util.context.Context apply(reactor.util.context.Context ctx) {
                        return ReactiveTraceHolder.write(ctx, span);
                    }
                });
    }

    /**
     * 结束并上报 SERVER Span（防重复 finish）。
     *
     * @param exchange 当前交换
     * @param error    异常；无则 null
     */
    private void finalizeSpan(ServerWebExchange exchange, Throwable error) {
        TraceSpan span = (TraceSpan) exchange.getAttributes().remove(SPAN_EXCHANGE_ATTR);
        if (span == null || span.isFinished()) {
            return;
        }

        HttpStatus statusCode = exchange.getResponse().getStatusCode();
        int status = statusCode != null ? statusCode.value() : 200;
        span.addTag("http.status_code", String.valueOf(status));

        if (error != null) {
            String msg = error.getMessage() != null ? error.getMessage() : "";
            span.finish("EXCEPTION", error.getClass().getName() + ": " + msg);
        } else if (status >= 400) {
            span.finish("HTTP_" + status, "HTTP Status: " + status);
        } else {
            span.finish();
        }

        spanReportingListener.reportSpan(span);
    }

    /**
     * 解析客户端 IP（优先 X-Forwarded-For）。
     *
     * @param request 请求
     * @return IP
     */
    private static String clientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            InetSocketAddress remote = request.getRemoteAddress();
            if (remote != null && remote.getAddress() != null) {
                ip = remote.getAddress().getHostAddress();
            } else {
                ip = "";
            }
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "";
    }
}
