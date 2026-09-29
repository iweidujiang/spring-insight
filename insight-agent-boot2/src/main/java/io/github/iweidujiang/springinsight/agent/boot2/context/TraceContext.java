/*
 * Copyright (c) 2026, 苏渡苇. All rights reserved.
 *
 * TraceContext：基于 ThreadLocal 的 Span 调用栈（Boot2 Servlet 同步请求链路）。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.context;

import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import org.springframework.core.NamedThreadLocal;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class TraceContext {

    private static final ThreadLocal<Deque<TraceSpan>> SPAN_STACK =
            new NamedThreadLocal<Deque<TraceSpan>>("Spring Insight Boot2 Trace Context") {
                @Override
                protected Deque<TraceSpan> initialValue() {
                    return new ArrayDeque<TraceSpan>();
                }
            };

    private TraceContext() {
    }

    public static Optional<TraceSpan> currentSpan() {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        return stack.isEmpty() ? Optional.<TraceSpan>empty() : Optional.of(stack.peek());
    }

    /**
     * 开始一个新的 Span 并压入栈。
     *
     * @param operationName 操作名
     * @return 压栈后的 Span
     */
    public static TraceSpan startSpan(String operationName) {
        return startSpan(operationName, null, null);
    }

    /**
     * 开始 Span：优先挂到本地栈顶；栈空且提供远程上下文时延续跨服务 Trace。
     *
     * @param operationName      操作名
     * @param remoteTraceId      入站 traceparent 的 TraceId；无则 null
     * @param remoteParentSpanId 入站 parent SpanId；无则 null
     * @return 压栈后的 Span
     */
    public static TraceSpan startSpan(String operationName, String remoteTraceId, String remoteParentSpanId) {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        TraceSpan parent = stack.isEmpty() ? null : stack.peek();
        TraceSpan span;
        if (parent != null) {
            span = new TraceSpan(parent.getTraceId(), parent.getSpanId());
        } else if (StringUtils.hasText(remoteTraceId) && StringUtils.hasText(remoteParentSpanId)) {
            span = new TraceSpan(remoteTraceId.trim(), remoteParentSpanId.trim());
        } else {
            span = new TraceSpan();
        }
        span.setOperationName(operationName);
        stack.push(span);
        return span;
    }

    public static Optional<TraceSpan> endSpan() {
        return endSpan(null, null);
    }

    public static Optional<TraceSpan> endSpan(String errorCode, String errorMessage) {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        TraceSpan span = stack.pop();
        span.finish(errorCode, errorMessage);
        return Optional.of(span);
    }

    public static void clear() {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        while (!stack.isEmpty()) {
            TraceSpan span = stack.pop();
            if (!span.isFinished()) {
                span.finish("CONTEXT_CLEARED", "上下文被强制清理");
            }
        }
        SPAN_STACK.remove();
    }
}
