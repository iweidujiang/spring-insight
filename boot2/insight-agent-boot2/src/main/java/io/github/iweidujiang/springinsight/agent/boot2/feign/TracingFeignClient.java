/**
 * Feign Client decorator: create CLIENT Span with remoteService for topology edges.
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

    /** Wrapped Feign Client */
    private final Client delegate;

    /** Insight properties (lazy) */
    private final ObjectProvider<InsightBoot2Properties> insightProperties;

    /** Span reporter (lazy) */
    private final ObjectProvider<SpanReportingListener> spanReportingListener;

    /**
     * @param delegate              original Client
     * @param insightProperties     config provider
     * @param spanReportingListener reporter provider
     */
    public TracingFeignClient(Client delegate,
                              ObjectProvider<InsightBoot2Properties> insightProperties,
                              ObjectProvider<SpanReportingListener> spanReportingListener) {
        this.delegate = delegate;
        this.insightProperties = insightProperties;
        this.spanReportingListener = spanReportingListener;
    }

    /**
     * Execute Feign call; when parent SERVER span exists, report a CLIENT child span.
     *
     * @param request Feign request
     * @param options timeouts
     * @return downstream response
     * @throws IOException on IO failure
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
        // Prefer @FeignClient name so url=http://127.0.0.1 still maps to service id on topology
        String remote = resolveRemoteService(request);
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
     * Resolve remote service id: Feign Target name first, then URL host.
     *
     * @param request Feign request
     * @return service name or host
     */
    static String resolveRemoteService(Request request) {
        if (request == null) {
            return "unknown";
        }
        try {
            if (request.requestTemplate() != null && request.requestTemplate().feignTarget() != null) {
                String name = request.requestTemplate().feignTarget().name();
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        } catch (Exception ignored) {
            // fall through to URL host
        }
        return resolveRemoteServiceFromUrl(request.url());
    }

    /**
     * Resolve host from URL (tests / fallback).
     *
     * @param url request URL
     * @return host or unknown
     */
    static String resolveRemoteServiceFromUrl(String url) {
        try {
            URI u = URI.create(url);
            if (u.getHost() != null && !u.getHost().isEmpty()) {
                return u.getHost();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return "unknown";
    }

    /**
     * Backward-compatible helper for unit tests.
     *
     * @param url request URL
     * @return host or unknown
     */
    static String resolveRemoteService(String url) {
        return resolveRemoteServiceFromUrl(url);
    }

    /**
     * Extract path from URL.
     *
     * @param url request URL
     * @return path or /
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
     * Compact operation label: host + path + optional query.
     *
     * @param url request URL
     * @return short label
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
