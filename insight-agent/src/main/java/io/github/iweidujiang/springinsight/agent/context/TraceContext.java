package io.github.iweidujiang.springinsight.agent.context;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NamedThreadLocal;
import org.springframework.util.StringUtils;

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
     * 开始一个新的 Span 并压入栈（无远程父级时新建根，栈非空时挂到栈顶）。
     *
     * @param operationName 操作名
     * @return 压栈后的 Span
     */
    public static TraceSpan startSpan(String operationName) {
        return startSpan(operationName, null, null, null, 1.0d);
    }

    /**
     * 开始 Span：优先挂到本地栈顶；栈空且提供远程上下文时延续跨服务 Trace。
     * <p>兼容旧调用：远程默认视为已采样，本地根默认全采。</p>
     *
     * @param operationName      操作名
     * @param remoteTraceId      入站 {@code traceparent} 的 TraceId；无则 null
     * @param remoteParentSpanId 入站 parent SpanId；无则 null
     * @return 压栈后的 Span
     */
    public static TraceSpan startSpan(String operationName, String remoteTraceId, String remoteParentSpanId) {
        return startSpan(operationName, remoteTraceId, remoteParentSpanId, null, 1.0d);
    }

    /**
     * 开始 Span，并应用头部采样：子 Span 继承父；远程跟随 flags；本地根按 sampleRate 决策。
     *
     * @param operationName      操作名
     * @param remoteTraceId      远程 TraceId；无则 null
     * @param remoteParentSpanId 远程 parent SpanId；无则 null
     * @param remoteSampled      远程是否采样；null 表示无远程或未知（按已采样）
     * @param sampleRate         本地根采样率
     * @return 压栈后的 Span
     */
    public static TraceSpan startSpan(String operationName, String remoteTraceId, String remoteParentSpanId,
                                      Boolean remoteSampled, double sampleRate) {
        Deque<TraceSpan> stack = SPAN_STACK.get();

        TraceSpan parentSpan = stack.isEmpty() ? null : stack.peek();
        TraceSpan span;

        if (parentSpan != null) {
            // 进程内子 Span：忽略远程头，挂本地父
            span = new TraceSpan(parentSpan.getTraceId(), parentSpan.getSpanId());
            span.setSampled(parentSpan.isSampled());
            log.debug("[追踪上下文] 创建子Span: traceId={}, parentSpanId={}, spanId={}, operation={}",
                    span.getTraceId(), span.getParentSpanId(), span.getSpanId(), operationName);
        } else if (StringUtils.hasText(remoteTraceId) && StringUtils.hasText(remoteParentSpanId)) {
            // 跨服务延续：与上游共享 traceId，parent 为对端注入的 SpanId
            span = new TraceSpan(remoteTraceId.trim(), remoteParentSpanId.trim());
            span.setSampled(remoteSampled == null || remoteSampled);
            log.debug("[追踪上下文] 延续远程Span: traceId={}, parentSpanId={}, spanId={}, operation={}, sampled={}",
                    span.getTraceId(), span.getParentSpanId(), span.getSpanId(), operationName, span.isSampled());
        } else {
            span = new TraceSpan();
            span.setSampled(TraceSampler.decide(sampleRate));
            log.debug("[追踪上下文] 创建根Span: traceId={}, spanId={}, operation={}, sampled={}",
                    span.getTraceId(), span.getSpanId(), operationName, span.isSampled());
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

    /**
     * 捕获当前线程 Trace 栈快照（Span 引用共享，便于子线程挂到同一父 Span）。
     */
    public static Snapshot capture() {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        if (stack == null || stack.isEmpty()) {
            return Snapshot.EMPTY;
        }
        return new Snapshot(new ArrayDeque<>(stack));
    }

    /**
     * 安装快照到当前线程；传入空快照则清除。
     */
    public static void install(Snapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            SPAN_STACK.remove();
            return;
        }
        SPAN_STACK.set(new ArrayDeque<>(snapshot.spans()));
    }

    /**
     * 在指定 Trace 快照下执行任务，结束后恢复原上下文。
     */
    public static void runWith(Snapshot snapshot, Runnable task) {
        Snapshot previous = capture();
        try {
            install(snapshot);
            task.run();
        } finally {
            install(previous);
        }
    }

    /**
     * Trace 栈不可变快照（提交线程捕获，工作线程安装）。
     */
    public static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(new ArrayDeque<>());

        private final Deque<TraceSpan> spans;

        private Snapshot(Deque<TraceSpan> spans) {
            this.spans = spans;
        }

        boolean isEmpty() {
            return spans == null || spans.isEmpty();
        }

        Deque<TraceSpan> spans() {
            return spans;
        }
    }
}
