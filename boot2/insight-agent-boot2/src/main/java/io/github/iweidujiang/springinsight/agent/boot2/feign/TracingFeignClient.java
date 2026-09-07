/**
 * Feign Client ?????????? CLIENT Span???? remoteService ??????
 *
 * @since?2026-09-07
 * @author???? ???????
 *
 * GitHub?https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.feign;

import feign.Client;
import feign.Request;
import feign.Request.Options;
import feign.Response;
import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

public class TracingFeignClient implements Client {

    /** ?????? Feign Client */
    private final Client delegate;

    /** Insight ???????? */
    private final ObjectProvider<InsightBoot2Properties> insightProperties;

    /** Span ?????????? */
    private final ObjectProvider<SpanReportingListener> spanReportingListener;

    /**
     * @param delegate              ?? Client
     * @param insightProperties     ?????
     * @param spanReportingListener ????????
     */
    public TracingFeignClient(Client delegate,
                              ObjectProvider<InsightBoot2Properties> insightProperties,
                              ObjectProvider<SpanReportingListener> spanReportingListener) {
        this.delegate = delegate;
        this.insightProperties = insightProperties;
        this.spanReportingListener = spanReportingListener;
    }

    /**
     * ?? Feign ?????? Span ??? HTTP ?????? CLIENT Span ????
     *
     * @param request Feign ??
     * @param options ?????
     * @return ????
     * @throws IOException ????????????
     */
    @Override
    public Response execute(Request request, Options options) throws IOException {
        InsightBoot2Properties props = insightProperties.getIfAvailable();
        SpanReportingListener listener = spanReportingListener.getIfAvailable();
        if (props == null || listener == null || !props.isHttpTracingEnabled()) {
            return delegate.execute(request, options);
        }

        Optional<TraceSpan> parentOpt = TraceContext.currentSpan();
        if (!parentOpt.isPresent()) {
            return delegate.execute(request, options);
        }

        String url = request.url();
        // remoteService???????? URL host
        String remote = resolveRemoteService(url);
        String path = safePath(url);
        TraceSpan parent = parentOpt.get();
        TraceSpan clientSpan = new TraceSpan(parent.getTraceId(), parent.getSpanId());
        clientSpan.setSpanKind("CLIENT");
        clientSpan.setComponent("OpenFeign");
        clientSpan.setServiceName(props.getServiceName());
        clientSpan.setOperationName(request.httpMethod().name() + " " + compactOp(url));
        clientSpan.setRemoteService(remote);
        clientSpan.setRemoteEndpoint(path);

        try {
            Response response = delegate.execute(request, options);
            int status = response.status();
            clientSpan.addTag("http.status_code", String.valueOf(status));
            if (status >= 400) {
                clientSpan.finish("HTTP_" + status, "HTTP Status: " + status);
            } else {
                clientSpan.finish();
            }
            listener.reportSpan(TraceSpan.snapshot(clientSpan));
            return response;
        } catch (IOException e) {
            clientSpan.finish("IO_ERROR", e.getMessage());
            listener.reportSpan(TraceSpan.snapshot(clientSpan));
            throw e;
        }
    }

    /**
     * ??? URL ?????????host??
     *
     * @param url Feign ?? URL
     * @return host???????? {@code unknown}
     */
    static String resolveRemoteService(String url) {
        try {
            URI u = URI.create(url);
            if (u.getHost() != null && !u.getHost().isEmpty()) {
                return u.getHost();
            }
        } catch (Exception ignored) {
            // ?????? unknown
        }
        return "unknown";
    }

    /**
     * ?? URL path?
     *
     * @param url Feign URL
     * @return path??? {@code /}
     */
    static String safePath(String url) {
        try {
            URI u = URI.create(url);
            return u.getPath() != null ? u.getPath() : "/";
        } catch (Exception e) {
            return "/";
        }
    }

    /**
     * ??????host + path + ?? query?
     *
     * @param url Feign URL
     * @return ??????
     */
    static String compactOp(String url) {
        try {
            URI u = URI.create(url);
            String q = u.getQuery();
            String p = u.getPath() != null ? u.getPath() : "";
            String host = u.getHost() != null ? u.getHost() : "";
            return host + p + (q != null ? "?" + q : "");
        } catch (Exception e) {
            return url;
        }
    }
}
