package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.context.ReactiveTraceHolder;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.net.URI;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * Gateway 出站追踪：代理下游时创建 CLIENT Span，并写入 {@code remoteService} 供拓扑聚合。
 * <p>
 * 父 Span 取入口 {@link ReactiveInsightWebFilter} 挂在 exchange 上的 SERVER Span。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class InsightGatewayTracingFilter implements GlobalFilter, Ordered {

    static final String CLIENT_SPAN_ATTR = InsightGatewayTracingFilter.class.getName() + ".clientSpan";

    private final SpanReportingListener spanReportingListener;
    private final InsightProperties insightProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!insightProperties.isHttpTracingEnabled()) {
            return chain.filter(exchange);
        }

        TraceSpan parent = (TraceSpan) exchange.getAttributes().get(ReactiveInsightWebFilter.SPAN_EXCHANGE_ATTR);
        String remote = resolveRemoteService(exchange);
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        String method = exchange.getRequest().getMethod().name();

        TraceSpan clientSpan = parent != null
                ? new TraceSpan(parent.getTraceId(), parent.getSpanId())
                : new TraceSpan();
        clientSpan.setSpanKind("CLIENT");
        clientSpan.setComponent("SpringCloudGateway");
        clientSpan.setOperationName(method + " " + path);
        clientSpan.setRemoteService(remote);
        clientSpan.setRemoteEndpoint(path);
        clientSpan.addTag("http.method", method)
                .addTag("http.path", path)
                .addTag("gateway.remote", remote);

        exchange.getAttributes().put(CLIENT_SPAN_ATTR, clientSpan);

        TraceSpan contextSpan = parent != null ? parent : clientSpan;

        return chain.filter(exchange)
                .doOnError(err -> finalizeClientSpan(exchange, err))
                .doFinally(signal -> {
                    if (signal != SignalType.ON_ERROR) {
                        finalizeClientSpan(exchange, null);
                    }
                })
                .contextWrite(ctx -> ReactiveTraceHolder.write(ctx, contextSpan));
    }

    private void finalizeClientSpan(ServerWebExchange exchange, Throwable error) {
        TraceSpan span = (TraceSpan) exchange.getAttributes().remove(CLIENT_SPAN_ATTR);
        if (span == null || span.isFinished()) {
            return;
        }
        int status = exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value()
                : 200;
        span.addTag("http.status_code", String.valueOf(status));
        if (error != null) {
            span.finish("EXCEPTION", error.getClass().getSimpleName() + ": "
                    + (error.getMessage() != null ? error.getMessage() : ""));
        } else if (status >= 400) {
            span.finish("HTTP_" + status, "HTTP Status: " + status);
        } else {
            span.finish();
        }
        spanReportingListener.reportSpan(TraceSpan.snapshot(span));
        if (insightProperties.isDiagnosticLogs()) {
            log.info("[Gateway追踪] CLIENT 结束: remote={}, status={}, duration={}ms",
                    span.getRemoteService(), status, span.getDurationMs());
        }
    }

    private static String resolveRemoteService(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route != null && route.getUri() != null) {
            URI uri = route.getUri();
            if ("lb".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) {
                return uri.getHost();
            }
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return uri.getHost();
            }
        }
        URI requestUrl = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        if (requestUrl != null && requestUrl.getHost() != null) {
            return requestUrl.getHost();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        // 尽量晚于路由解析、早于写响应收尾
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
