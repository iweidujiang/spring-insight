package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import io.github.iweidujiang.springinsight.agent.boot2.autoconfigure.InsightBoot2Properties;
import io.github.iweidujiang.springinsight.agent.boot2.context.RemoteServiceResolver;
import io.github.iweidujiang.springinsight.agent.boot2.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.boot2.context.W3cTracePropagator;
import io.github.iweidujiang.springinsight.agent.boot2.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;

/**
 * RestTemplate 出站 CLIENT Span（Boot2）。
 * <p>
 * 仅在存在父 Span 且开启 HTTP 追踪时创建子 Span；不压入 {@link TraceContext} 栈。
 * 开启 {@code spring.insight.http-trace-propagation-enabled} 时注入 W3C {@code traceparent}。
 * 须通过 {@code RestTemplateBuilder} 注入；{@code new RestTemplate()} 不会生效。
 * </p>
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public class InsightClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InsightClientHttpRequestInterceptor.class);

    private final SpanReportingListener spanReportingListener;
    private final InsightBoot2Properties insightProperties;
    /** 组件名：Boot2 仅 RestTemplate */
    private final String component;

    /**
     * @param spanReportingListener 上报入口
     * @param insightProperties     开关配置
     * @param component             一般为 {@code RestTemplate}
     */
    public InsightClientHttpRequestInterceptor(SpanReportingListener spanReportingListener,
                                               InsightBoot2Properties insightProperties,
                                               String component) {
        this.spanReportingListener = spanReportingListener;
        this.insightProperties = insightProperties;
        this.component = StringUtils.hasText(component) ? component : "RestTemplate";
    }

    /**
     * 拦截出站请求：有父 Span 时创建 CLIENT 子 Span 并上报。
     *
     * @param request   请求
     * @param body      请求体
     * @param execution 下游执行器
     * @return 响应
     * @throws IOException 网络或 IO 失败
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!insightProperties.isHttpTracingEnabled()) {
            return execution.execute(request, body);
        }
        if (!TraceContext.currentSpan().isPresent()) {
            return execution.execute(request, body);
        }

        TraceSpan parent = TraceContext.currentSpan().get();
        URI uri = request.getURI();
        String method = request.getMethod() != null ? request.getMethod().name() : "GET";
        String path = uri.getPath() != null && !uri.getPath().isEmpty() ? uri.getPath() : "/";
        String query = uri.getRawQuery();

        TraceSpan clientSpan = new TraceSpan(parent.getTraceId(), parent.getSpanId());
        clientSpan.setSampled(parent.isSampled());
        clientSpan.setSpanKind("CLIENT");
        clientSpan.setComponent(component);
        clientSpan.setOperationName(method + " " + compactOp(uri));
        clientSpan.setRemoteService(resolveRemoteService(uri, request.getHeaders().getFirst("Host")));
        clientSpan.setRemoteEndpoint(path);
        clientSpan.addTag("http.method", method)
                .addTag("http.path", path)
                .addTag("http.query", query != null ? query : "");

        if (insightProperties.isHttpTracePropagationEnabled()) {
            boolean injected = W3cTracePropagator.inject(
                    clientSpan.getTraceId(),
                    clientSpan.getSpanId(),
                    clientSpan.isSampled(),
                    new W3cTracePropagator.HeaderSetter() {
                        @Override
                        public void set(String name, String value) {
                            request.getHeaders().set(name, value);
                        }
                    });
            if (injected) {
                clientSpan.addTag("insight.propagation", "w3c");
            }
        }

        try {
            ClientHttpResponse response = execution.execute(request, body);
            int status = response.getStatusCode().value();
            clientSpan.addTag("http.status_code", String.valueOf(status));
            // status≥400：与 Feign 一致记为 HTTP_xxx
            if (status >= 400) {
                clientSpan.finish("HTTP_" + status, "HTTP Status: " + status);
            } else {
                clientSpan.finish();
            }
            spanReportingListener.reportSpan(TraceSpan.snapshot(clientSpan));
            if (insightProperties.isDiagnosticLogs()) {
                log.info("[HTTP客户端追踪] {} 结束: remote={}, status={}, duration={}ms",
                        component, clientSpan.getRemoteService(), Integer.valueOf(status),
                        clientSpan.getDurationMs());
            }
            return response;
        } catch (IOException e) {
            clientSpan.finish("IO_ERROR", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            spanReportingListener.reportSpan(TraceSpan.snapshot(clientSpan));
            throw e;
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
