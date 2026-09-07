package io.github.iweidujiang.springinsight.agent.boot2.instrumentation;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * InsightWebClientExchangeFilter 单测：remoteService / compactOp。
 *
 * @since 2026-09-07
 * @author 公众号：苏渡苗 GitHub：https://github.com/iweidujiang
 */
public class InsightWebClientExchangeFilterTest {

    /**
     * 校验 host / lb:// 解析。
     */
    @Test
    public void resolveRemoteService_usesHost() {
        assertEquals("sca-order", InsightWebClientExchangeFilter.resolveRemoteService(
                URI.create("http://sca-order/api/orders")));
        assertEquals("sca-user", InsightWebClientExchangeFilter.resolveRemoteService(
                URI.create("lb://sca-user/users/1")));
        assertEquals("unknown", InsightWebClientExchangeFilter.resolveRemoteService(null));
    }

    /**
     * 校验操作名压缩。
     */
    @Test
    public void compactOp_includesHostPathQuery() {
        assertEquals("sca-order/api/x?a=1", InsightWebClientExchangeFilter.compactOp(
                URI.create("http://sca-order/api/x?a=1")));
    }
}
