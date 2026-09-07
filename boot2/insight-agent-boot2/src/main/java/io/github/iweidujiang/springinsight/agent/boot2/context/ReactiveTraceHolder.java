package io.github.iweidujiang.springinsight.agent.boot2.context;

import io.github.iweidujiang.springinsight.agent.boot2.model.TraceSpan;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Optional;

/**
 * WebFlux 侧 Trace 载体：存在 Reactor Context，跨事件循环线程仍可取到父 Span。
 *
 * @since 2026-09-07
 * @author 公众号：苏渡苗 GitHub：https://github.com/iweidujiang
 */
public final class ReactiveTraceHolder {

    /** Reactor Context 键 */
    public static final String CONTEXT_KEY = ReactiveTraceHolder.class.getName() + ".span";

    /**
     * 禁止实例化。
     */
    private ReactiveTraceHolder() {
    }

    /**
     * 将 SERVER/当前 Span 写入 Reactor Context。
     *
     * @param ctx  原 Context
     * @param span 待挂载 Span；null 时不修改
     * @return 新 Context
     */
    public static Context write(Context ctx, TraceSpan span) {
        if (span == null) {
            return ctx;
        }
        return ctx.put(CONTEXT_KEY, span);
    }

    /**
     * 从 ContextView 读取当前 Span。
     *
     * @param ctx Reactor ContextView
     * @return Optional Span
     */
    public static Optional<TraceSpan> current(ContextView ctx) {
        if (ctx == null || !ctx.hasKey(CONTEXT_KEY)) {
            return Optional.empty();
        }
        Object v = ctx.get(CONTEXT_KEY);
        if (v instanceof TraceSpan) {
            return Optional.of((TraceSpan) v);
        }
        return Optional.empty();
    }
}
