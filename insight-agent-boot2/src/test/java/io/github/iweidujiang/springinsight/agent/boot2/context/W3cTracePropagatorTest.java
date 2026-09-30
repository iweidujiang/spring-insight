package io.github.iweidujiang.springinsight.agent.boot2.context;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link W3cTracePropagator} Boot2 编解码单测。
 *
 * @since 2026-09-29
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public class W3cTracePropagatorTest {

    /**
     * format → inject → extract 往返。
     */
    @Test
    public void injectAndExtract_roundTrip() {
        final Map<String, String> headers = new HashMap<String, String>();
        assertTrue(W3cTracePropagator.inject(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                new W3cTracePropagator.HeaderSetter() {
                    @Override
                    public void set(String name, String value) {
                        headers.put(name, value);
                    }
                }));
        Optional<W3cTracePropagator.RemoteContext> extracted = W3cTracePropagator.extract(
                new W3cTracePropagator.HeaderGetter() {
                    @Override
                    public String get(String name) {
                        return headers.get(name);
                    }
                });
        assertTrue(extracted.isPresent());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", extracted.get().getTraceId());
        assertEquals("00f067aa0ba902b7", extracted.get().getParentSpanId());
        assertTrue(extracted.get().isSampled());
    }

    /**
     * flags=00 解析为未采样；format 可写出未采样。
     */
    @Test
    public void parseAndFormat_notSampled() {
        Optional<W3cTracePropagator.RemoteContext> ctx = W3cTracePropagator.parseTraceparent(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00");
        assertTrue(ctx.isPresent());
        assertTrue(!ctx.get().isSampled());
        Optional<String> formatted = W3cTracePropagator.formatTraceparent(
                "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", false);
        assertTrue(formatted.isPresent());
        assertTrue(formatted.get().endsWith("-00"));
    }

    /**
     * 全 0 ID 拒绝。
     */
    @Test
    public void parse_rejectsAllZero() {
        assertTrue(!W3cTracePropagator.parseTraceparent(
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01").isPresent());
    }
}
