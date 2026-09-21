package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * InsightClientHttpRequestInterceptor 解析单测。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
public class InsightClientHttpRequestInterceptorTest {

    /**
     * 校验 host / lb:// 解析。
     */
    @Test
    public void resolveRemoteService_usesHost() {
        assertEquals("sca-order", InsightClientHttpRequestInterceptor.resolveRemoteService(
                URI.create("http://sca-order/api/orders")));
        assertEquals("order", InsightClientHttpRequestInterceptor.resolveRemoteService(
                URI.create("lb://order/items")));
        assertEquals("unknown", InsightClientHttpRequestInterceptor.resolveRemoteService(null));
    }

    /**
     * 校验操作名压缩。
     */
    @Test
    public void compactOp_includesHostPathQuery() {
        assertEquals("sca-order/api/x?a=1", InsightClientHttpRequestInterceptor.compactOp(
                URI.create("http://sca-order/api/x?a=1")));
    }
}
