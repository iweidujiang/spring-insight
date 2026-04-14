package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
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
 * WebFlux / Gateway 入口 HTTP 追踪（不使用 ThreadLocal {@code TraceContext}，避免线程切换导致上下文丢失）。
 */
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ReactiveInsightWebFilter implements WebFilter {

    static final String SPAN_EXCHANGE_ATTR = ReactiveInsightWebFilter.class.getName() + ".span";

    private final SpanReportingListener spanReportingListener;
    private final InsightProperties insightProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!insightProperties.isHttpTracingEnabled()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().pathWithinApplication().value();
        for (String pattern : insightProperties.resolveExcludePatterns()) {
            if (pathMatcher.match(pattern, path)) {
                return chain.filter(exchange);
            }
        }

        String method = request.getMethod().name();
        String operationName = method + " " + path;
        TraceSpan span = new TraceSpan();
        span.setOperationName(operationName);
        span.setSpanKind("SERVER");
        span.setComponent("SpringWebFlux");
        String rawQuery = request.getURI().getRawQuery();
        span.addTag("http.method", method)
                .addTag("http.path", path)
                .addTag("http.query", rawQuery != null ? rawQuery : "")
                .addTag("http.client_ip", clientIp(request))
                .addTag("http.user_agent", Optional.ofNullable(request.getHeaders().getFirst(HttpHeaders.USER_AGENT)).orElse(""));

        if (insightProperties.isDiagnosticLogs()) {
            log.info("[WebFlux追踪] 开始: traceId={}, spanId={}, {}", span.getTraceId(), span.getSpanId(), operationName);
        }

        exchange.getAttributes().put(SPAN_EXCHANGE_ATTR, span);

        return chain.filter(exchange)
                .doOnError(ex -> finalizeSpan(exchange, ex))
                .doFinally(signal -> {
                    if (signal != SignalType.ON_ERROR) {
                        finalizeSpan(exchange, null);
                    }
                });
    }

    private void finalizeSpan(ServerWebExchange exchange, Throwable error) {
        TraceSpan span = (TraceSpan) exchange.getAttributes().remove(SPAN_EXCHANGE_ATTR);
        if (span == null || span.isFinished()) {
            return;
        }

        int status = exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value()
                : 200;
        span.addTag("http.status_code", String.valueOf(status));

        if (error != null) {
            span.finish("EXCEPTION", error.getClass().getName() + ": " + Optional.ofNullable(error.getMessage()).orElse(""));
        } else if (status >= 400) {
            span.finish("HTTP_" + status, "HTTP Status: " + status);
        } else {
            span.finish(null, null);
        }

        spanReportingListener.reportSpan(span);
    }

    private static String clientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            InetSocketAddress remote = request.getRemoteAddress();
            ip = remote != null && remote.getAddress() != null
                    ? remote.getAddress().getHostAddress()
                    : "";
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "";
    }
}
