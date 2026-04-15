package io.github.iweidujiang.springinsight.feign;

import feign.Client;
import feign.Request;
import feign.Request.Options;
import feign.Response;
import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

/**
 * 为 OpenFeign 出站调用创建带 {@code remoteService} 的 CLIENT Span，供拓扑与依赖统计（{@code TraceSpanPersistenceService#getServiceDependencies}）。
 */
@Slf4j
public class TracingFeignClient implements Client {

    private final Client delegate;
    private final ObjectProvider<InsightProperties> insightProperties;
    private final ObjectProvider<SpanReportingListener> spanReportingListener;

    public TracingFeignClient(Client delegate,
                              ObjectProvider<InsightProperties> insightProperties,
                              ObjectProvider<SpanReportingListener> spanReportingListener) {
        this.delegate = delegate;
        this.insightProperties = insightProperties;
        this.spanReportingListener = spanReportingListener;
    }

    @Override
    public Response execute(Request request, Options options) throws IOException {
        InsightProperties props = insightProperties.getIfAvailable();
        SpanReportingListener listener = spanReportingListener.getIfAvailable();
        if (props == null || listener == null || !props.isHttpTracingEnabled()) {
            return delegate.execute(request, options);
        }

        Optional<TraceSpan> parentOpt = TraceContext.currentSpan();
        if (parentOpt.isEmpty()) {
            return delegate.execute(request, options);
        }

        String url = request.url();
        String remote = resolveRemoteService(url);
        String path = safePath(url);
        TraceSpan parent = parentOpt.get();
        TraceSpan clientSpan = new TraceSpan(parent.getTraceId(), parent.getSpanId());
        clientSpan.setSpanKind("CLIENT");
        clientSpan.setComponent("OpenFeign");
        clientSpan.setOperationName(request.httpMethod().name() + " " + compactOp(url));
        clientSpan.setRemoteService(remote);
        clientSpan.setRemoteEndpoint(path);

        try {
            Response response = delegate.execute(request, options);
            clientSpan.addTag("http.status_code", String.valueOf(response.status()));
            clientSpan.setSuccess(response.status() < 400);
            clientSpan.finish();
            listener.reportSpan(TraceSpan.snapshot(clientSpan));
            return response;
        } catch (IOException e) {
            clientSpan.finish("IO_ERROR", e.getMessage());
            listener.reportSpan(TraceSpan.snapshot(clientSpan));
            throw e;
        }
    }

    static String resolveRemoteService(String url) {
        try {
            URI u = URI.create(url);
            if (u.getHost() != null && !u.getHost().isEmpty()) {
                return u.getHost();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    static String safePath(String url) {
        try {
            URI u = URI.create(url);
            return u.getPath() != null ? u.getPath() : "/";
        } catch (Exception e) {
            return "/";
        }
    }

    static String compactOp(String url) {
        try {
            URI u = URI.create(url);
            String q = u.getQuery();
            String p = u.getPath() != null ? u.getPath() : "";
            return u.getHost() + p + (q != null ? "?" + q : "");
        } catch (Exception e) {
            return url;
        }
    }
}
