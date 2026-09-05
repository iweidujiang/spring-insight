package io.github.iweidujiang.springinsight.agent.instrumentation;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InsightWebClientExchangeFilterTest {

    @Test
    void resolveRemoteService_usesHost() {
        assertEquals("sca-order", InsightWebClientExchangeFilter.resolveRemoteService(
                URI.create("http://sca-order/api/orders")));
        assertEquals("sca-user", InsightWebClientExchangeFilter.resolveRemoteService(
                URI.create("lb://sca-user/users/1")));
        assertEquals("unknown", InsightWebClientExchangeFilter.resolveRemoteService(null));
    }

    @Test
    void compactOp_includesHostPathQuery() {
        assertEquals("sca-order/api/x?a=1", InsightWebClientExchangeFilter.compactOp(
                URI.create("http://sca-order/api/x?a=1")));
    }
}
