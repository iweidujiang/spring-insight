package io.github.iweidujiang.springinsight.agent.context;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link TraceContext#startSpan(String, String, String)} 远程延续单测。
 *
 * @since 2026-09-29
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class TraceContextRemoteContinueTest {

    /**
     * 清理 ThreadLocal，避免污染其它用例。
     */
    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    /**
     * 栈空 + 远程上下文 → 共享 TraceId，parent 为远程 SpanId。
     */
    @Test
    void startSpan_continuesRemoteWhenStackEmpty() {
        String remoteTrace = "4bf92f3577b34da6a3ce929d0e0e4736";
        String remoteParent = "00f067aa0ba902b7";
        TraceSpan span = TraceContext.startSpan("GET /x", remoteTrace, remoteParent);
        assertEquals(remoteTrace, span.getTraceId());
        assertEquals(remoteParent, span.getParentSpanId());
        assertEquals(16, span.getSpanId().length());
    }

    /**
     * 栈非空时忽略远程头，挂本地父。
     */
    @Test
    void startSpan_prefersLocalParentOverRemote() {
        TraceSpan root = TraceContext.startSpan("root");
        TraceSpan child = TraceContext.startSpan("child", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb");
        assertEquals(root.getTraceId(), child.getTraceId());
        assertEquals(root.getSpanId(), child.getParentSpanId());
    }

    /**
     * 无远程信息时创建新根。
     */
    @Test
    void startSpan_newRootWithoutRemote() {
        TraceSpan span = TraceContext.startSpan("alone", null, null);
        assertNull(span.getParentSpanId());
        assertEquals(32, span.getTraceId().length());
        assertEquals(16, span.getSpanId().length());
    }
}
