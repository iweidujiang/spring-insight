package io.github.iweidujiang.springinsight.agent.instrumentation;

import io.github.iweidujiang.springinsight.agent.context.TraceContext;
import org.springframework.core.task.TaskDecorator;

/**
 * 将提交线程的 Trace 栈透传到工作线程（{@code @Async} / ThreadPoolTaskExecutor）。
 * <p>
 * 可与已有 {@link TaskDecorator} 组合：本装饰器在提交线程先捕获上下文，再交给下游装饰器。
 * </p>
 */
public class InsightTaskDecorator implements TaskDecorator {

    private final TaskDecorator delegate;

    public InsightTaskDecorator() {
        this(null);
    }

    public InsightTaskDecorator(TaskDecorator delegate) {
        this.delegate = delegate;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        TraceContext.Snapshot snapshot = TraceContext.capture();
        Runnable insightWrapped = () -> TraceContext.runWith(snapshot, runnable);
        if (delegate != null) {
            return delegate.decorate(insightWrapped);
        }
        return insightWrapped;
    }
}
