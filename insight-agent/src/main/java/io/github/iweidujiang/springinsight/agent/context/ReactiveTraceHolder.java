package io.github.iweidujiang.springinsight.agent.context;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Optional;

/**
 * WebFlux / Gateway 侧 Trace 载体：存在 Reactor {@link Context} 中，跨事件循环线程仍可取到父 Span。
 */
public final class ReactiveTraceHolder {

    /** Reactor Context 键 */
    public static final String CONTEXT_KEY = ReactiveTraceHolder.class.getName() + ".span";

    private ReactiveTraceHolder() {
    }

    public static Context write(Context ctx, TraceSpan span) {
        if (span == null) {
            return ctx;
        }
        return ctx.put(CONTEXT_KEY, span);
    }

    public static Optional<TraceSpan> current(ContextView ctx) {
        if (ctx == null || !ctx.hasKey(CONTEXT_KEY)) {
            return Optional.empty();
        }
        Object v = ctx.get(CONTEXT_KEY);
        return v instanceof TraceSpan span ? Optional.of(span) : Optional.empty();
    }
}
