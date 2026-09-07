package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.context.ReactiveTraceHolder;
import io.github.iweidujiang.springinsight.agent.boot2.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * WebClient 出站 CLIENT Span：填充 remoteService 供拓扑；父 Span 优先 Reactor Context，其次 ThreadLocal。
 *
 * @since 2026-09-07
 * @author 公众号：苏渡苗 GitHub：https://github.com/iweidujiang
 */
public class InsightWebClientExchangeFilter implements ExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(InsightWebClientExchangeFilter.class);

    private final SpanReportingListener spanReportingListener;
    private final InsightBoot2Properties insightProperties;

    /**
     * @param spanReportingListener 上报入口
     * @param insightProperties     配置
     */
    public InsightWebClientExchangeFilter(SpanReportingListener spanReportingListener,
                                          InsightBoot2Properties insightProperties) {
        this.spanReportingListener = spanReportingListener;
        this.insightProperties = insightProperties;
    }

    /**
     * 在存在父 Span 时创建 CLIENT 子 Span 并上报。
     *
     * @param request 出站请求
     * @param next    下游 ExchangeFunction
     * @return 响应 Mono
     */
    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        if (!insightProperties.isHttpTracingEnabled()) {
            return next.exchange(request);
        }

        return Mono.deferContextual(new Function<ContextView, Mono<ClientResponse>>() {
            @Override
            public Mono<ClientResponse> apply(ContextView ctxView) {
                Optional<TraceSpan> parentOpt = ReactiveTraceHolder.current(ctxView);
                if (!parentOpt.isPresent()) {
                    parentOpt = TraceContext.currentSpan();
                }
                if (!parentOpt.isPresent()) {
                    return next.exchange(request);
                }

                TraceSpan parent = parentOpt.get();
                URI uri = request.url();
                String remote = resolveRemoteService(uri);
                String path = uri.getPath() != null && !uri.getPath().isEmpty() ? uri.getPath() : "/";
                String method = request.method().name();
                String query = uri.getRawQuery();

                TraceSpan clientSpan = new TraceSpan(parent.getTraceId(), parent.getSpanId());
                clientSpan.setSpanKind("CLIENT");
                clientSpan.setComponent("WebClient");
                clientSpan.setServiceName(insightProperties.getServiceName());
                clientSpan.setOperationName(method + " " + compactOp(uri));
                clientSpan.setRemoteService(remote);
                clientSpan.setRemoteEndpoint(path);
                clientSpan.addTag("http.method", method)
                        .addTag("http.path", path)
                        .addTag("http.query", query != null ? query : "");

                final AtomicBoolean reported = new AtomicBoolean(false);

                return next.exchange(request)
                        .doOnSuccess(new java.util.function.Consumer<ClientResponse>() {
                            @Override
                            public void accept(ClientResponse response) {
                                finalizeSpan(clientSpan, response, null, reported);
                            }
                        })
                        .doOnError(new java.util.function.Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable error) {
                                finalizeSpan(clientSpan, null, error, reported);
                            }
                        });
            }
        });
    }

    /**
     * 结束 CLIENT Span 并上报（仅一次）。
     *
     * @param span     CLIENT Span
     * @param response 响应；异常时可 null
     * @param error    异常；成功时 null
     * @param reported 防重复上报标志
     */
    private void finalizeSpan(TraceSpan span, ClientResponse response, Throwable error, AtomicBoolean reported) {
        if (!reported.compareAndSet(false, true) || span.isFinished()) {
            return;
        }
        if (error != null) {
            String msg = error.getMessage() != null ? error.getMessage() : "";
            span.finish("EXCEPTION", error.getClass().getSimpleName() + ": " + msg);
        } else if (response != null) {
            int status = response.statusCode().value();
            span.addTag("http.status_code", String.valueOf(status));
            if (status >= 400) {
                span.finish("HTTP_" + status, "HTTP Status: " + status);
            } else {
                span.finish();
            }
        } else {
            span.finish();
        }
        spanReportingListener.reportSpan(TraceSpan.snapshot(span));
        if (insightProperties.isDiagnosticLogs()) {
            log.info("[Boot2-WebClient] CLIENT 结束: remote={}, duration={}ms, status={}",
                    span.getRemoteService(), span.getDurationMs(), span.getStatusCode());
        }
    }

    /**
     * 解析 remoteService：host（含 lb://serviceId）。
     *
     * @param uri 请求 URI
     * @return 服务名或 host
     */
    static String resolveRemoteService(URI uri) {
        if (uri == null) {
            return "unknown";
        }
        if (uri.getHost() != null && !uri.getHost().trim().isEmpty()) {
            return uri.getHost();
        }
        return "unknown";
    }

    /**
     * 压缩操作名：host + path + 可选 query。
     *
     * @param uri 请求 URI
     * @return 短标签
     */
    static String compactOp(URI uri) {
        if (uri == null) {
            return "";
        }
        String host = uri.getHost() != null ? uri.getHost() : "";
        String path = uri.getPath() != null ? uri.getPath() : "";
        String q = uri.getRawQuery();
        return host + path + (q != null ? "?" + q : "");
    }
}
