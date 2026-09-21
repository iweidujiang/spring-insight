package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.context.ReactiveTraceHolder;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * Gateway 出站追踪：代理下游时创建 CLIENT Span，并写入 remoteService 供拓扑聚合。
 * <p>
 * 父 Span 取入口 {@link ReactiveInsightWebFilter} 挂在 exchange 上的 SERVER Span。
 * </p>
 *
 * @since 2026-09-07
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public class InsightBoot2GatewayTracingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InsightBoot2GatewayTracingFilter.class);

    /** exchange 上的 CLIENT Span 属性键 */
    static final String CLIENT_SPAN_ATTR = InsightBoot2GatewayTracingFilter.class.getName() + ".clientSpan";

    private final SpanReportingListener spanReportingListener;
    private final InsightBoot2Properties insightProperties;

    /**
     * @param spanReportingListener 上报入口
     * @param insightProperties     配置
     */
    public InsightBoot2GatewayTracingFilter(SpanReportingListener spanReportingListener,
                                            InsightBoot2Properties insightProperties) {
        this.spanReportingListener = spanReportingListener;
        this.insightProperties = insightProperties;
    }

    /**
     * 创建 Gateway 代理 CLIENT Span；路由解析后、写响应前执行。
     *
     * @param exchange 当前交换
     * @param chain    Gateway Filter 链
     * @return 空 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!insightProperties.isHttpTracingEnabled()) {
            return chain.filter(exchange);
        }

        // 父：入口 WebFilter 挂在 exchange 上的 SERVER Span
        TraceSpan parent = (TraceSpan) exchange.getAttributes().get(ReactiveInsightWebFilter.SPAN_EXCHANGE_ATTR);
        String remote = resolveRemoteService(exchange);
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        HttpMethod httpMethod = exchange.getRequest().getMethod();
        String method = httpMethod != null ? httpMethod.name() : "UNKNOWN";

        TraceSpan clientSpan = parent != null
                ? new TraceSpan(parent.getTraceId(), parent.getSpanId())
                : new TraceSpan();
        clientSpan.setSpanKind("CLIENT");
        clientSpan.setComponent("SpringCloudGateway");
        clientSpan.setServiceName(insightProperties.getServiceName());
        clientSpan.setOperationName(method + " " + path);
        clientSpan.setRemoteService(remote);
        clientSpan.setRemoteEndpoint(path);
        clientSpan.addTag("http.method", method)
                .addTag("http.path", path)
                .addTag("gateway.remote", remote);

        exchange.getAttributes().put(CLIENT_SPAN_ATTR, clientSpan);

        // Context 保留 SERVER 父（无父时用 CLIENT），供同链出站读取
        final TraceSpan contextSpan = parent != null ? parent : clientSpan;

        return chain.filter(exchange)
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable err) {
                        finalizeClientSpan(exchange, err);
                    }
                })
                .doFinally(new Consumer<SignalType>() {
                    @Override
                    public void accept(SignalType signal) {
                        if (signal != SignalType.ON_ERROR) {
                            finalizeClientSpan(exchange, null);
                        }
                    }
                })
                .contextWrite(new Function<reactor.util.context.Context, reactor.util.context.Context>() {
                    @Override
                    public reactor.util.context.Context apply(reactor.util.context.Context ctx) {
                        return ReactiveTraceHolder.write(ctx, contextSpan);
                    }
                });
    }

    /**
     * 结束并上报 CLIENT Span（防重复 finish）。
     *
     * @param exchange 当前交换
     * @param error    异常；无则 null
     */
    private void finalizeClientSpan(ServerWebExchange exchange, Throwable error) {
        TraceSpan span = (TraceSpan) exchange.getAttributes().remove(CLIENT_SPAN_ATTR);
        if (span == null || span.isFinished()) {
            return;
        }
        HttpStatus statusCode = exchange.getResponse().getStatusCode();
        // status>=400 记为失败；无状态码时按 200 处理
        int status = statusCode != null ? statusCode.value() : 200;
        span.addTag("http.status_code", String.valueOf(status));
        if (error != null) {
            String msg = error.getMessage() != null ? error.getMessage() : "";
            span.finish("EXCEPTION", error.getClass().getSimpleName() + ": " + msg);
        } else if (status >= 400) {
            span.finish("HTTP_" + status, "HTTP Status: " + status);
        } else {
            span.finish();
        }
        spanReportingListener.reportSpan(TraceSpan.snapshot(span));
        if (insightProperties.isDiagnosticLogs()) {
            log.info("[Boot2-Gateway] CLIENT 结束: remote={}, status={}, duration={}ms",
                    span.getRemoteService(), status, span.getDurationMs());
        }
    }

    /**
     * 解析 remoteService：优先路由 URI（lb 取 host 即服务名），其次 GATEWAY_REQUEST_URL。
     *
     * @param exchange 当前交换
     * @return 服务名或 host；无法解析时为 unknown
     */
    static String resolveRemoteService(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route != null && route.getUri() != null) {
            URI uri = route.getUri();
            if ("lb".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) {
                return uri.getHost();
            }
            if (uri.getHost() != null && !uri.getHost().trim().isEmpty()) {
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
     * Filter 顺序：尽量晚于路由解析、早于写响应收尾。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
