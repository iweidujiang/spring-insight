/**
 * Feign Client 装饰器：创建带 remoteService 的 CLIENT Span，供拓扑画边。
 *
 * @since：2026-09-07
 * @author：苏渡苗 公众号：苏渡苗
 *
 * GitHub：https://github.com/iweidujiang
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

/**
 * 包装 Feign {@link Client}，在存在父 SERVER Span 时上报 CLIENT 子 Span。
 */
public class TracingFeignClient implements Client {

    /** 被包装的原始 Feign Client */
    private final Client delegate;

    /** Insight 配置（延迟获取） */
    private final ObjectProvider<InsightBoot2Properties> insightProperties;

    /** Span 上报入口（延迟获取） */
    private final ObjectProvider<SpanReportingListener> spanReportingListener;

    /**
     * @param delegate              原始 Client
     * @param insightProperties     配置 Provider
     * @param spanReportingListener 上报 Provider
     */
    public TracingFeignClient(Client delegate,
                              ObjectProvider<InsightBoot2Properties> insightProperties,
                              ObjectProvider<SpanReportingListener> spanReportingListener) {
        this.delegate = delegate;
        this.insightProperties = insightProperties;
        this.spanReportingListener = spanReportingListener;
    }

    /**
     * 执行 Feign 调用；存在父 SERVER Span 时上报 CLIENT 子 Span。
     *
     * @param request Feign 请求
     * @param options 超时等选项
     * @return 下游响应
     * @throws IOException IO 失败时抛出
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
        // 优先 @FeignClient name，url 直连 IP 时拓扑仍显示服务名
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
     * 解析下游服务标识：优先 Feign Target 名，其次 URL host。
     *
     * @param request Feign 请求
     * @return 服务名或 host
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
            // 回退到 URL host
        }
        return resolveRemoteServiceFromUrl(request.url());
    }

    /**
     * 从 URL 解析 host（单测 / 回退）。
     *
     * @param url 请求 URL
     * @return host 或 unknown
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
     * 兼容旧单测入口：按 URL 解析 remoteService。
     *
     * @param url 请求 URL
     * @return host 或 unknown
     */
    static String resolveRemoteService(String url) {
        return resolveRemoteServiceFromUrl(url);
    }

    /**
     * 从 URL 提取 path。
     *
     * @param url 请求 URL
     * @return path，失败时为 /
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
     * 压缩操作名：host + path + 可选 query。
     *
     * @param url 请求 URL
     * @return 短标签
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
