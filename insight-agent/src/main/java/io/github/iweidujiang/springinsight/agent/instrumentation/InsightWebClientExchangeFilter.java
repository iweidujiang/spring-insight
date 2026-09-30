package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.context.ReactiveTraceHolder;
import io.github.iweidujiang.springinsight.agent.context.RemoteServiceResolver;
import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.context.W3cTracePropagator;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebClient 出站追踪：CLIENT Span、{@code remoteService}，以及可选 W3C {@code traceparent} 注入。
 * <p>
 * 父 Span 优先取 Reactor {@link ReactiveTraceHolder}，其次 {@link TraceContext}（Servlet 线程池场景）。
 * 通过 {@code WebClient.Builder} 的 {@code WebClientCustomizer} 注入；手写 {@code WebClient.create()} 不会生效。
 * </p>
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Slf4j
@RequiredArgsConstructor
public class InsightWebClientExchangeFilter implements ExchangeFilterFunction {

    private final SpanReportingListener spanReportingListener;
    private final InsightProperties insightProperties;

    /**
     * 拦截 WebClient 出站：有父 Span 时创建 CLIENT 子 Span，并按开关注入 {@code traceparent}。
     *
     * @param request 出站请求
     * @param next    下游交换函数
     * @return 响应 Mono
     */
    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        if (!insightProperties.isHttpTracingEnabled()) {
            return next.exchange(request);
        }

        return Mono.deferContextual(ctxView -> {
            Optional<TraceSpan> parentOpt = ReactiveTraceHolder.current(ctxView)
                    .or(TraceContext::currentSpan);
            if (parentOpt.isEmpty()) {
                return next.exchange(request);
            }

            TraceSpan parent = parentOpt.get();
            URI uri = request.url();
            String remote = resolveRemoteService(uri, request.headers().getFirst(HttpHeaders.HOST));
            String path = uri.getPath() != null && !uri.getPath().isEmpty() ? uri.getPath() : "/";
            String method = request.method().name();
            String query = uri.getRawQuery();

            TraceSpan clientSpan = new TraceSpan(parent.getTraceId(), parent.getSpanId());
            clientSpan.setSampled(parent.isSampled());
            clientSpan.setSpanKind("CLIENT");
            clientSpan.setComponent("WebClient");
            clientSpan.setOperationName(method + " " + compactOp(uri));
            clientSpan.setRemoteService(remote);
            clientSpan.setRemoteEndpoint(path);
            clientSpan.addTag("http.method", method)
                    .addTag("http.path", path)
                    .addTag("http.query", query != null ? query : "");

            ClientRequest outbound = request;
            if (insightProperties.isHttpTracePropagationEnabled()) {
                Optional<String> tp = W3cTracePropagator.formatTraceparent(
                        clientSpan.getTraceId(), clientSpan.getSpanId(), clientSpan.isSampled());
                if (tp.isPresent()) {
                    // ClientRequest 不可变：复制并追加 traceparent
                    outbound = ClientRequest.from(request)
                            .header(W3cTracePropagator.TRACEPARENT_HEADER, tp.get())
                            .build();
                    clientSpan.addTag("insight.propagation", "w3c");
                }
            }

            AtomicBoolean reported = new AtomicBoolean(false);

            return next.exchange(outbound)
                    .doOnSuccess(response -> finalizeSpan(clientSpan, response, null, reported))
                    .doOnError(error -> finalizeSpan(clientSpan, null, error, reported));
        });
    }

    /**
     * 结束并上报 CLIENT Span（成功 / 错误各至多一次）。
     *
     * @param span     CLIENT Span
     * @param response 响应，可为 null
     * @param error    异常，可为 null
     * @param reported 是否已上报标志
     */
    private void finalizeSpan(TraceSpan span, ClientResponse response, Throwable error, AtomicBoolean reported) {
        if (!reported.compareAndSet(false, true) || span.isFinished()) {
            return;
        }
        if (error != null) {
            span.finish("EXCEPTION", error.getClass().getSimpleName() + ": "
                    + (error.getMessage() != null ? error.getMessage() : ""));
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
            log.info("[WebClient追踪] CLIENT 结束: remote={}, duration={}ms, status={}",
                    span.getRemoteService(), span.getDurationMs(), span.getStatusCode());
        }
    }

    /**
     * @param uri        请求 URI
     * @param hostHeader Host 头
     * @return 规范化 remoteService
     */
    static String resolveRemoteService(URI uri, String hostHeader) {
        String logical = null;
        if (uri != null && "lb".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) {
            logical = uri.getHost();
        }
        return RemoteServiceResolver.resolve(uri, hostHeader, logical);
    }

    /**
     * @param uri 请求 URI
     * @return 规范化 remoteService
     */
    static String resolveRemoteService(URI uri) {
        return resolveRemoteService(uri, null);
    }

    /**
     * @param uri 请求 URI
     * @return {@code host+path(?query)}
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
