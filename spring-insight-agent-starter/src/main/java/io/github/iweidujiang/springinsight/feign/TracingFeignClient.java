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
 * Feign {@link Client} 装饰器：在出站调用上创建 CLIENT Span，并填充 {@code remoteService} 供拓扑聚合。
 *
 * @author 苏渡苇
 * @since 2026/7/28
 */
@Slf4j
public class TracingFeignClient implements Client {

    /** 被包装的原始 Feign Client（通常为 LoadBalancerFeignClient） */
    private final Client delegate;

    /** Insight 配置提供者 */
    private final ObjectProvider<InsightProperties> insightProperties;

    /** Span 上报监听器提供者 */
    private final ObjectProvider<SpanReportingListener> spanReportingListener;

    /**
     * @param delegate               原始 Client
     * @param insightProperties      配置
     * @param spanReportingListener  上报入口
     */
    public TracingFeignClient(Client delegate,
                              ObjectProvider<InsightProperties> insightProperties,
                              ObjectProvider<SpanReportingListener> spanReportingListener) {
        this.delegate = delegate;
        this.insightProperties = insightProperties;
        this.spanReportingListener = spanReportingListener;
    }

    /**
     * 执行 Feign 请求：有父 Span 且开启 HTTP 追踪时创建子 CLIENT Span 并上报。
     *
     * @param request Feign 请求
     * @param options 超时等选项
     * @return 下游响应
     * @throws IOException 委托调用或网络失败时抛出
     */
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
        // remoteService：拓扑边上的目标节点（当前取 URL host）
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
     * 从请求 URL 解析远程服务标识（host）。
     *
     * @param url Feign 完整 URL
     * @return host；无法解析时返回 {@code unknown}
     */
    static String resolveRemoteService(String url) {
        try {
            URI u = URI.create(url);
            if (u.getHost() != null && !u.getHost().isEmpty()) {
                return u.getHost();
            }
        } catch (Exception ignored) {
            // 忽略解析失败，返回 unknown
        }
        return "unknown";
    }

    /**
     * 提取 URL path，供 {@code remoteEndpoint} 使用。
     *
     * @param url Feign URL
     * @return path，默认 {@code /}
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
     * 压缩操作名展示：host + path + 可选 query。
     *
     * @param url Feign URL
     * @return 简短操作描述
     */
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
