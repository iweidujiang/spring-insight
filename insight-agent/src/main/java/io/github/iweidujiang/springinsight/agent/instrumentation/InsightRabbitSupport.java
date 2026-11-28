package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import io.github.iweidujiang.springinsight.agent.context.W3cTracePropagator;
import io.github.iweidujiang.springinsight.agent.listener.SpanReportingListener;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * RabbitMQ 生产端 CLIENT Span 辅助：exchange / routingKey、慢阈值、消息头透传（不压入 Trace 栈）。
 *
 * @author 苏渡苇
 * @since 2026-11-28
 */
public final class InsightRabbitSupport {

    private static final ThreadLocal<TraceSpan> CURRENT_PUBLISH = new ThreadLocal<>();
    private static final int ROUTING_KEY_MAX = 120;

    private InsightRabbitSupport() {
    }

    /**
     * @return 当前发布中的 CLIENT Span（供 beforePublishPostProcessor 注入头）
     */
    public static TraceSpan currentPublishSpan() {
        return CURRENT_PUBLISH.get();
    }

    /**
     * 从 {@link org.springframework.amqp.rabbit.core.RabbitTemplate} 发布方法参数解析 exchange / routingKey。
     *
     * @param args 方法参数
     * @return [exchange, routingKey]；缺省为空串
     */
    public static String[] resolveExchangeAndRoutingKey(Object[] args) {
        String exchange = "";
        String routingKey = "";
        if (args == null || args.length == 0) {
            return new String[]{exchange, routingKey};
        }
        if (args[0] instanceof String first) {
            if (args.length > 1 && args[1] instanceof String second) {
                exchange = first;
                routingKey = second;
            } else {
                routingKey = first;
            }
        }
        return new String[]{exchange, routingKey};
    }

    /**
     * @param properties 配置
     * @param durationMs 耗时
     * @param error      是否异常结束
     * @return 是否应上报
     */
    public static boolean shouldReport(InsightProperties properties, long durationMs, boolean error) {
        if (error) {
            return true;
        }
        if (properties == null) {
            return true;
        }
        long threshold = properties.getRabbitSlowThresholdMs();
        if (threshold <= 0L) {
            return true;
        }
        return durationMs >= threshold;
    }

    /**
     * 开始一次生产端 Span（无父 Span 或未开启追踪时返回 empty）。
     *
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param props      配置
     * @return CLIENT Span
     */
    public static Optional<TraceSpan> beginPublish(String exchange, String routingKey, InsightProperties props) {
        if (props == null || !props.isEnabled() || !props.isRabbitTracingEnabled()) {
            return Optional.empty();
        }
        Optional<TraceSpan> parentOpt = TraceContext.currentSpan();
        if (parentOpt.isEmpty()) {
            return Optional.empty();
        }
        TraceSpan parent = parentOpt.get();
        TraceSpan span = new TraceSpan(parent.getTraceId(), parent.getSpanId());
        span.setSampled(parent.isSampled());
        span.setSpanKind("CLIENT");
        span.setComponent("RABBITMQ");
        String ex = exchange == null ? "" : exchange.trim();
        String rk = routingKey == null ? "" : routingKey.trim();
        span.setOperationName(StringUtils.hasText(ex) ? "RABBIT PRODUCE " + ex : "RABBIT PRODUCE");
        span.setRemoteService(StringUtils.hasText(ex) ? ex : "rabbitmq");
        if (StringUtils.hasText(rk)) {
            span.setRemoteEndpoint(rk.length() > ROUTING_KEY_MAX ? rk.substring(0, ROUTING_KEY_MAX) + "…" : rk);
        }
        span.addTag("messaging.system", "rabbitmq");
        span.addTag("messaging.operation", "publish");
        if (StringUtils.hasText(ex)) {
            span.addTag("messaging.destination", ex);
        }
        if (StringUtils.hasText(rk)) {
            span.addTag("messaging.rabbitmq.routing_key", rk);
        }
        CURRENT_PUBLISH.set(span);
        return Optional.of(span);
    }

    /**
     * 向消息头写入 {@code traceparent}（需开启 http 透传）。
     *
     * @param message 消息
     * @param span    CLIENT Span
     * @param props   配置
     */
    public static void injectTraceparent(Message message, TraceSpan span, InsightProperties props) {
        if (message == null || span == null || props == null || !props.isHttpTracePropagationEnabled()) {
            return;
        }
        MessageProperties mp = message.getMessageProperties();
        if (mp == null) {
            return;
        }
        boolean injected = W3cTracePropagator.inject(
                span.getTraceId(),
                span.getSpanId(),
                span.isSampled(),
                mp::setHeader);
        if (injected) {
            span.addTag("insight.propagation", "w3c");
        }
    }

    /**
     * 结束发布 Span 并按需上报。
     *
     * @param span     begin 返回的 Span；可为 empty
     * @param error    异常；成功为 null
     * @param props    配置
     * @param listener 上报
     * @param startMs  开始毫秒
     */
    public static void endPublish(Optional<TraceSpan> span,
                                  Throwable error,
                                  InsightProperties props,
                                  SpanReportingListener listener,
                                  long startMs) {
        try {
            if (span == null || span.isEmpty()) {
                return;
            }
            TraceSpan client = span.get();
            long duration = Math.max(0L, System.currentTimeMillis() - startMs);
            if (error != null) {
                client.finish("RABBIT_ERROR", error.getMessage() != null
                        ? error.getMessage()
                        : error.getClass().getSimpleName());
                report(listener, client);
                return;
            }
            if (!shouldReport(props, duration, false)) {
                return;
            }
            if (props != null
                    && props.getRabbitSlowThresholdMs() > 0L
                    && duration >= props.getRabbitSlowThresholdMs()) {
                client.addTag("messaging.rabbitmq.slow", "true");
            }
            client.finish();
            report(listener, client);
        } finally {
            CURRENT_PUBLISH.remove();
        }
    }

    /**
     * 执行发布并按需上报 CLIENT Span。
     *
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param props      配置
     * @param listener   上报
     * @param action     实际发布
     * @param <T>        返回类型
     * @return 调用结果
     * @throws Exception 透传
     */
    public static <T> T tracePublish(String exchange,
                                     String routingKey,
                                     InsightProperties props,
                                     SpanReportingListener listener,
                                     RabbitCallable<T> action) throws Exception {
        Optional<TraceSpan> span = beginPublish(exchange, routingKey, props);
        long start = System.currentTimeMillis();
        try {
            T result = action.call();
            endPublish(span, null, props, listener, start);
            return result;
        } catch (Exception e) {
            endPublish(span, e, props, listener, start);
            throw e;
        } catch (Throwable t) {
            endPublish(span, t, props, listener, start);
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new Exception(t);
        }
    }

    private static void report(SpanReportingListener listener, TraceSpan span) {
        if (listener != null && span != null) {
            listener.reportSpan(TraceSpan.snapshot(span));
        }
    }

    /**
     * 可抛受检异常的 Rabbit 发布调用。
     *
     * @param <T> 返回类型
     */
    @FunctionalInterface
    public interface RabbitCallable<T> {
        /**
         * @return 调用结果
         * @throws Exception 发布或业务异常
         */
        T call() throws Exception;
    }
}
