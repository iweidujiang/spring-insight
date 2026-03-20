package io.github.iweidujiang.springinsight.agent.context;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NamedThreadLocal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * ┌───────────────────────────────────────────────┐
 * │ 📦 追踪上下文管理器（基于 ThreadLocal）
 * |    用于在当前线程中管理 TraceSpan 的调用栈
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────┘
 */
@Slf4j
public class TraceContext {

    /** 与 {@link io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties#diagnosticLogs} 同步 */
    private static volatile boolean diagnosticLogs = false;

    private static final ThreadLocal<Deque<TraceSpan>> SPAN_STACK =
            new NamedThreadLocal<>("Spring Insight Trace Context") {
                @Override
                protected Deque<TraceSpan> initialValue() {
                    return new ArrayDeque<>();
                }
            };

    private TraceContext() {
        // 私有构造器，防止实例化
    }

    public static void setDiagnosticLogs(boolean enabled) {
        diagnosticLogs = enabled;
    }

    /**
     * 获取当前 Span（栈顶元素）
     */
    public static Optional<TraceSpan> currentSpan() {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.peek());
    }

    /**
     * 获取当前 TraceId
     */
    public static Optional<String> currentTraceId() {
        return currentSpan().map(TraceSpan::getTraceId);
    }

    /**
     * 获取当前 SpanId
     */
    public static Optional<String> currentSpanId() {
        return currentSpan().map(TraceSpan::getSpanId);
    }

    /**
     * 开始一个新的 Span 并压入栈
     */
    public static TraceSpan startSpan(String operationName) {
        Deque<TraceSpan> stack = SPAN_STACK.get();

        TraceSpan parentSpan = stack.isEmpty() ? null : stack.peek();
        TraceSpan span;

        if (parentSpan == null) {
            // 创建根 Span
            span = new TraceSpan();
            log.debug("[追踪上下文] 创建根Span: traceId={}, spanId={}, operation={}",
                    span.getTraceId(), span.getSpanId(), operationName);
        } else {
            // 创建子 Span
            span = new TraceSpan(parentSpan.getTraceId(), parentSpan.getSpanId());
            log.debug("[追踪上下文] 创建子Span: traceId={}, parentSpanId={}, spanId={}, operation={}",
                    span.getTraceId(), span.getParentSpanId(), span.getSpanId(), operationName);
        }

        span.setOperationName(operationName);
        stack.push(span);

        return span;
    }

    /**
     * 结束当前 Span 并弹出栈
     */
    public static Optional<TraceSpan> endSpan() {
        return endSpan(null, null);
    }

    /**
     * 结束当前 Span 并弹出栈（带错误信息）
     */
    public static Optional<TraceSpan> endSpan(String errorCode, String errorMessage) {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        if (stack.isEmpty()) {
            if (diagnosticLogs) {
                log.warn("[追踪上下文] 尝试结束Span，但当前上下文栈为空");
            } else {
                log.trace("[追踪上下文] 尝试结束Span，但当前上下文栈为空");
            }
            return Optional.empty();
        }

        TraceSpan span = stack.pop();
        span.finish(errorCode, errorMessage);

        log.debug("[追踪上下文] 结束Span: traceId={}, spanId={}, operation={}, duration={}ms",
                span.getTraceId(), span.getSpanId(), span.getOperationName(), span.getDurationMs());

        return Optional.of(span);
    }

    /**
     * 获取当前调用栈深度（用于调试）
     */
    public static int getStackDepth() {
        return SPAN_STACK.get().size();
    }

    /**
     * 清除当前线程的上下文（防止内存泄漏）
     */
    public static void clear() {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        if (!stack.isEmpty()) {
            if (diagnosticLogs) {
                log.warn("[追踪上下文] 强制清除非空上下文栈，栈深度: {}", stack.size());
            } else {
                log.debug("[追踪上下文] 强制清除非空上下文栈，栈深度: {}", stack.size());
            }
            while (!stack.isEmpty()) {
                TraceSpan span = stack.pop();
                if (!span.isFinished()) {
                    span.finish("CONTEXT_CLEARED", "上下文被强制清理");
                    if (diagnosticLogs) {
                        log.warn("[追踪上下文] 强制结束未完成Span: spanId={}", span.getSpanId());
                    } else {
                        log.debug("[追踪上下文] 强制结束未完成Span: spanId={}", span.getSpanId());
                    }
                }
            }
        }
        SPAN_STACK.remove();
        log.debug("[追踪上下文] 已清除当前线程上下文");
    }

    /**
     * 获取当前完整的Span栈快照（用于调试）
     */
    public static String getStackSnapshot() {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        StringBuilder sb = new StringBuilder("Span栈[深度=").append(stack.size()).append("]: ");
        int i = 0;
        for (TraceSpan span : stack) {
            if (i++ > 0) sb.append(" -> ");
            sb.append(span.getSpanId()).append("(").append(span.getOperationName()).append(")");
        }
        return sb.toString();
    }

    /**
     * 设置当前 Span 的 remoteService 字段
     */
    public static void setRemoteService(String remoteService) {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        if (!stack.isEmpty()) {
            TraceSpan span = stack.peek();
            span.setRemoteService(remoteService);
            log.debug("[追踪上下文] 设置当前Span的remoteService: spanId={}, remoteService={}", span.getSpanId(), remoteService);
        } else {
            if (diagnosticLogs) {
                log.warn("[追踪上下文] 尝试设置remoteService，但当前上下文栈为空");
            } else {
                log.trace("[追踪上下文] 尝试设置remoteService，但当前上下文栈为空");
            }
        }
    }
}
