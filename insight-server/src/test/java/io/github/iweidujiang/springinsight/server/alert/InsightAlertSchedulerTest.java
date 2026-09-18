package io.github.iweidujiang.springinsight.server.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import io.github.iweidujiang.springinsight.server.config.InsightServerAlertProperties;
import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;
import io.github.iweidujiang.springinsight.server.settings.InsightRuntimeSettingsService;
import io.github.iweidujiang.springinsight.storage.impl.InMemorySpanStore;
import io.github.iweidujiang.springinsight.storage.service.TraceSpanPersistenceService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 告警扫描：超阈值 POST、冷却跳过、失败不冷却。
 *
 * @since 2026-09-18
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class InsightAlertSchedulerTest {

    private HttpServer server;

    /**
     * 关闭本地 webhook。
     */
    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 错误率超阈值发送一次；冷却期内第二次只记 cooldown。
     *
     * @throws IOException 起本地 HTTP 失败
     */
    @Test
    void scanPostsThenCooldown() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        AtomicReference<String> body = new AtomicReference<>();
        int port = startHook(204, hits, body);

        InsightAlertScheduler scheduler = scheduler(port, "error_rate", 10.0, 30);
        scheduler.scan();
        scheduler.scan();

        assertEquals(1, hits.get());
        assertTrue(body.get().contains("\"serviceName\":\"sca-order\""));
        assertTrue(body.get().contains("\"metric\":\"error_rate\""));
        assertEquals(1.0, lastMetrics.count("success"));
        assertEquals(1.0, lastMetrics.count("cooldown"));
    }

    /**
     * 未达阈值不发送。
     *
     * @throws IOException 起本地 HTTP 失败
     */
    @Test
    void belowThresholdDoesNotPost() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        int port = startHook(204, hits, new AtomicReference<>());
        InsightAlertScheduler scheduler = scheduler(port, "error_rate", 90.0, 30);
        scheduler.scan();
        assertEquals(0, hits.get());
    }

    /**
     * 非 2xx 记 failure，且下一轮仍会重试（不进入冷却）。
     *
     * @throws IOException 起本地 HTTP 失败
     */
    @Test
    void failureDoesNotCooldown() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        int port = startHook(500, hits, new AtomicReference<>());
        InsightServerAlertProperties props = baseProps(port, "error_count", 1.0, 30);
        TraceSpanPersistenceService persistence = storeWithOneError();
        InsightAlertMetrics metrics = new InsightAlertMetrics(new SimpleMeterRegistry());
        InsightRuntimeSettingsService settings = mock(InsightRuntimeSettingsService.class);
        when(settings.effectiveAlert()).thenReturn(props);
        InsightAlertScheduler scheduler = new InsightAlertScheduler(
                settings, persistence, new InsightAlertWebhookSender(new ObjectMapper()),
                new InsightAlertEmailSender(), metrics);
        scheduler.scan();
        scheduler.scan();
        assertEquals(2, hits.get());
        assertEquals(2.0, metrics.count("failure"));
        assertEquals(0.0, metrics.count("success"));
    }

    private InsightAlertMetrics lastMetrics;

    private InsightAlertScheduler scheduler(int port, String metric, double threshold, int cooldown) {
        InsightServerAlertProperties props = baseProps(port, metric, threshold, cooldown);
        lastMetrics = new InsightAlertMetrics(new SimpleMeterRegistry());
        InsightRuntimeSettingsService settings = mock(InsightRuntimeSettingsService.class);
        when(settings.effectiveAlert()).thenReturn(props);
        return new InsightAlertScheduler(
                settings,
                storeWithOneError(),
                new InsightAlertWebhookSender(new ObjectMapper()),
                new InsightAlertEmailSender(),
                lastMetrics);
    }

    private InsightServerAlertProperties baseProps(int port, String metric, double threshold, int cooldown) {
        InsightServerAlertProperties props = new InsightServerAlertProperties();
        props.setEnabled(true);
        props.setWebhookUrl("http://127.0.0.1:" + port + "/hook");
        props.setMetric(metric);
        props.setThreshold(threshold);
        props.setWindowMinutes(15);
        props.setCooldownMinutes(cooldown);
        return props;
    }

    private static TraceSpanPersistenceService storeWithOneError() {
        InsightServerStorageProperties storage = new InsightServerStorageProperties();
        InMemorySpanStore store = new InMemorySpanStore(storage, "memory");
        TraceSpanPersistenceService persistence = new TraceSpanPersistenceService(store);
        TraceSpan err = new TraceSpan();
        err.setServiceName("sca-order");
        err.setSuccess(false);
        err.setStatusCode("ERROR");
        err.setStartTime(System.currentTimeMillis());
        TraceSpan ok = new TraceSpan();
        ok.setServiceName("sca-order");
        ok.setSuccess(true);
        ok.setStatusCode("OK");
        ok.setStartTime(System.currentTimeMillis());
        persistence.saveTraceSpans(List.of(err, ok));
        return persistence;
    }

    private int startHook(int status, AtomicInteger hits, AtomicReference<String> body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            hits.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return server.getAddress().getPort();
    }
}
