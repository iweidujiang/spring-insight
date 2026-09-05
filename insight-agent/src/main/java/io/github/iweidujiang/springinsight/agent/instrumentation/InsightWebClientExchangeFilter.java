package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.context.ReactiveTraceHolder;
import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebClient 出站追踪：创建 CLIENT Span 并填充 {@code remoteService} 供拓扑聚合。
 * <p>
 * 父 Span 优先取 Reactor {@link ReactiveTraceHolder}，其次 {@link TraceContext}（Servlet 线程池场景）。
 * 通过 {@code WebClient.Builder} 的 {@code WebClientCustomizer} 注入；手写 {@code WebClient.create()} 不会生效。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class InsightWebClientExchangeFilter implements ExchangeFilterFunction {

    private final SpanReportingListener spanReportingListener;
    private final InsightProperties insightProperties;

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
            String remote = resolveRemoteService(uri);
            String path = uri.getPath() != null && !uri.getPath().isEmpty() ? uri.getPath() : "/";
            String method = request.method().name();
            String query = uri.getRawQuery();

            TraceSpan clientSpan = new TraceSpan(parent.getTraceId(), parent.getSpanId());
            clientSpan.setSpanKind("CLIENT");
            clientSpan.setComponent("WebClient");
            clientSpan.setOperationName(method + " " + compactOp(uri));
            clientSpan.setRemoteService(remote);
            clientSpan.setRemoteEndpoint(path);
            clientSpan.addTag("http.method", method)
                    .addTag("http.path", path)
                    .addTag("http.query", query != null ? query : "");

            AtomicBoolean reported = new AtomicBoolean(false);

            return next.exchange(request)
                    .doOnSuccess(response -> finalizeSpan(clientSpan, response, null, reported))
                    .doOnError(error -> finalizeSpan(clientSpan, null, error, reported));
        });
    }

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

    static String resolveRemoteService(URI uri) {
        if (uri == null) {
            return "unknown";
        }
        // lb://service-id 时 host 即为服务名
        if (uri.getHost() != null && !uri.getHost().isBlank()) {
            return uri.getHost();
        }
        return "unknown";
    }

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
