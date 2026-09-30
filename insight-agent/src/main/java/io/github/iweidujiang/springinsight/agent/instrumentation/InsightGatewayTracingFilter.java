package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.context.ReactiveTraceHolder;
import io.github.iweidujiang.springinsight.agent.context.TraceSampler;
import io.github.iweidujiang.springinsight.agent.context.W3cTracePropagator;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.net.URI;
import java.util.Optional;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * Gateway 出站追踪：代理下游时创建 CLIENT Span，并可选注入 W3C {@code traceparent}。
 * <p>
 * 父 Span 取入口 {@link ReactiveInsightWebFilter} 挂在 exchange 上的 SERVER Span。
 * </p>
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@RequiredArgsConstructor
public class InsightGatewayTracingFilter implements GlobalFilter, Ordered {

    /** exchange 属性：本过滤器创建的 CLIENT Span */
    static final String CLIENT_SPAN_ATTR = InsightGatewayTracingFilter.class.getName() + ".clientSpan";

    private final SpanReportingListener spanReportingListener;
    private final InsightProperties insightProperties;

    /**
     * 代理下游前创建 CLIENT Span，并按开关注入 {@code traceparent} 到出站请求。
     *
     * @param exchange 当前交换
     * @param chain    过滤器链
     * @return 完成信号
     */
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
        if (parent != null) {
            clientSpan.setSampled(parent.isSampled());
        } else {
            clientSpan.setSampled(TraceSampler.decide(insightProperties.getSampleRate()));
        }
        clientSpan.setSpanKind("CLIENT");
        clientSpan.setComponent("SpringCloudGateway");
        clientSpan.setOperationName(method + " " + path);
        clientSpan.setRemoteService(remote);
        clientSpan.setRemoteEndpoint(path);
        clientSpan.addTag("http.method", method)
                .addTag("http.path", path)
                .addTag("gateway.remote", remote);

        ServerWebExchange outboundExchange = exchange;
        if (insightProperties.isHttpTracePropagationEnabled()) {
            Optional<String> tp = W3cTracePropagator.formatTraceparent(
                    clientSpan.getTraceId(), clientSpan.getSpanId(), clientSpan.isSampled());
            if (tp.isPresent()) {
                // 下游服务读该头延续同一 TraceId
                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                        .header(W3cTracePropagator.TRACEPARENT_HEADER, tp.get())
                        .build();
                outboundExchange = exchange.mutate().request(mutatedRequest).build();
                clientSpan.addTag("insight.propagation", "w3c");
            }
        }

        outboundExchange.getAttributes().put(CLIENT_SPAN_ATTR, clientSpan);

        TraceSpan contextSpan = parent != null ? parent : clientSpan;
        ServerWebExchange toFilter = outboundExchange;

        return chain.filter(toFilter)
                .doOnError(err -> finalizeClientSpan(toFilter, err))
                .doFinally(signal -> {
                    if (signal != SignalType.ON_ERROR) {
                        finalizeClientSpan(toFilter, null);
                    }
                })
                .contextWrite(ctx -> ReactiveTraceHolder.write(ctx, contextSpan));
    }

    /**
     * 结束并上报 CLIENT Span。
     *
     * @param exchange 当前交换
     * @param error    异常，可为 null
     */
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

    /**
     * 解析下游服务标识：优先路由 lb:// 服务名，其次已解析的请求 URL host。
     *
     * @param exchange 当前交换
     * @return remoteService
     */
    static String resolveRemoteService(ServerWebExchange exchange) {
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

    /**
     * @return 尽量晚于路由解析、早于写响应收尾
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
