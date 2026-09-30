package io.github.iweidujiang.springinsight.agent.context;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link W3cTracePropagator} 编解码单测。
 *
 * @since 2026-09-29
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class W3cTracePropagatorTest {

    /**
     * 合法头可解析，且大小写不敏感。
     */
    @Test
    void parse_validTraceparent() {
        String header = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        Optional<W3cTracePropagator.RemoteContext> ctx = W3cTracePropagator.parseTraceparent(header);
        assertTrue(ctx.isPresent());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", ctx.get().traceId());
        assertEquals("00f067aa0ba902b7", ctx.get().parentSpanId());
        assertTrue(ctx.get().sampled());
    }

    /**
     * flags=00 解析为未采样。
     */
    @Test
    void parse_notSampledFlag() {
        Optional<W3cTracePropagator.RemoteContext> ctx = W3cTracePropagator.parseTraceparent(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00");
        assertTrue(ctx.isPresent());
        assertFalse(ctx.get().sampled());
    }

    /**
     * format 可写入未采样 flags。
     */
    @Test
    void format_notSampled() {
        Optional<String> formatted = W3cTracePropagator.formatTraceparent(
                "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", false);
        assertTrue(formatted.isPresent());
        assertTrue(formatted.get().endsWith("-00"));
    }

    /**
     * 全 0 TraceId / SpanId 必须拒绝。
     */
    @Test
    void parse_rejectsAllZeroIds() {
        assertTrue(W3cTracePropagator.parseTraceparent(
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01").isEmpty());
        assertTrue(W3cTracePropagator.parseTraceparent(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01").isEmpty());
    }

    /**
     * 非法格式拒绝。
     */
    @Test
    void parse_rejectsMalformed() {
        assertTrue(W3cTracePropagator.parseTraceparent(null).isEmpty());
        assertTrue(W3cTracePropagator.parseTraceparent("").isEmpty());
        assertTrue(W3cTracePropagator.parseTraceparent("not-a-traceparent").isEmpty());
        assertTrue(W3cTracePropagator.parseTraceparent(
                "01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01").isEmpty());
    }

    /**
     * format → parse 往返一致；短 ID 左补 0。
     */
    @Test
    void format_roundTripAndPad() {
        Optional<String> formatted = W3cTracePropagator.formatTraceparent(
                "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7");
        assertTrue(formatted.isPresent());
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", formatted.get());

        Optional<String> shortIds = W3cTracePropagator.formatTraceparent("abc", "1");
        assertTrue(shortIds.isPresent());
        assertTrue(shortIds.get().startsWith("00-"));
        Optional<W3cTracePropagator.RemoteContext> parsed = W3cTracePropagator.parseTraceparent(shortIds.get());
        assertTrue(parsed.isPresent());
        assertEquals(32, parsed.get().traceId().length());
        assertEquals(16, parsed.get().parentSpanId().length());
    }

    /**
     * inject / extract 通过 Map 模拟请求头。
     */
    @Test
    void injectAndExtract_viaMap() {
        Map<String, String> headers = new HashMap<>();
        assertTrue(W3cTracePropagator.inject(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                headers::put));
        assertTrue(headers.containsKey(W3cTracePropagator.TRACEPARENT_HEADER));

        Optional<W3cTracePropagator.RemoteContext> extracted =
                W3cTracePropagator.extract(headers::get);
        assertTrue(extracted.isPresent());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", extracted.get().traceId());
        assertEquals("00f067aa0ba902b7", extracted.get().parentSpanId());
    }

    /**
     * 非十六进制 ID 无法 format。
     */
    @Test
    void format_rejectsNonHex() {
        assertFalse(W3cTracePropagator.formatTraceparent("zzzz", "00f067aa0ba902b7").isPresent());
    }
}
