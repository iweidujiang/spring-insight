package io.github.iweidujiang.springinsight.agent.context;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TraceSampler} 单测。
 *
 * @since 2026-09-30
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class TraceSamplerTest {

    /**
     * 边界：0 永不采，1 全采。
     */
    @Test
    void decide_boundaries() {
        assertFalse(TraceSampler.decide(0.0));
        assertFalse(TraceSampler.decide(-1.0));
        assertTrue(TraceSampler.decide(1.0));
        assertTrue(TraceSampler.decide(2.0));
    }

    /**
     * clamp 将 NaN / 越界夹到 [0,1]。
     */
    @Test
    void clamp_normalizes() {
        assertEquals(0.0, TraceSampler.clamp(Double.NaN));
        assertEquals(0.0, TraceSampler.clamp(-0.5));
        assertEquals(1.0, TraceSampler.clamp(1.5));
        assertEquals(0.3, TraceSampler.clamp(0.3));
    }

    /**
     * 已采样一律上报；未采样时只有错误且开关打开才上报。
     */
    @Test
    void shouldExport_keepsSuccessSampledAndForcesErrors() {
        TraceSpan success = new TraceSpan();
        success.setSampled(true);
        success.finish();
        assertTrue(TraceSampler.shouldExport(success, true));

        TraceSpan unsampledOk = new TraceSpan();
        unsampledOk.setSampled(false);
        unsampledOk.finish();
        assertFalse(TraceSampler.shouldExport(unsampledOk, true));
        assertFalse(TraceSampler.isError(unsampledOk));

        TraceSpan unsampledError = new TraceSpan();
        unsampledError.setSampled(false);
        unsampledError.finish("HTTP_500", "HTTP Status: 500");
        assertTrue(TraceSampler.isError(unsampledError));
        assertTrue(TraceSampler.shouldExport(unsampledError, true));
        assertFalse(TraceSampler.shouldExport(unsampledError, false));
        assertFalse(unsampledError.isSampled());
    }

    /**
     * 名单为空时不改变采样；丢弃优先于强制上报与错误强制上报。
     */
    @Test
    void pathRules_dropBeatsForceAndErrors() {
        TraceSpan ok = named("GET /api/orders/1", "/api/orders/1");
        ok.setSampled(true);
        ok.finish();
        assertTrue(TraceSampler.shouldExport(ok, true, new String[0], new String[0]));

        assertFalse(TraceSampler.shouldExport(ok, true, new String[]{"/api/orders/**"}, null));

        TraceSpan missed = named("GET /billing/charge", "/billing/charge");
        missed.setSampled(false);
        missed.finish();
        assertTrue(TraceSampler.shouldExport(missed, true, null, new String[]{"/billing/**"}));

        TraceSpan both = named("DB SELECT", null);
        both.setSampled(false);
        both.finish("SQL_ERROR", "boom");
        assertFalse(TraceSampler.shouldExport(both, true,
                new String[]{"DB *"}, new String[]{"DB *"}));
    }

    private static TraceSpan named(String operation, String httpPath) {
        TraceSpan span = new TraceSpan();
        span.setOperationName(operation);
        if (httpPath != null) {
            span.addTag("http.path", httpPath);
        }
        return span;
    }
}
