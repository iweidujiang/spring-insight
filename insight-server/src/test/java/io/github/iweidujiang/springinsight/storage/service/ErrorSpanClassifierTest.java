package io.github.iweidujiang.springinsight.storage.service;

import io.github.iweidujiang.springinsight.agent.model.TraceSpan;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ErrorSpanClassifier} 单元测试。
 *
 * @since 2026-09-14
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
class ErrorSpanClassifierTest {

    /**
     * http.status_code 标签优先归为 status。
     */
    @Test
    void classifyHttpStatusFromTag() {
        TraceSpan span = new TraceSpan();
        span.setSuccess(false);
        span.setStatusCode("ERROR");
        span.setTags(Map.of("http.status_code", "404"));
        ErrorSpanClassifier.Classification c = ErrorSpanClassifier.classify(span);
        assertEquals("status", c.kind());
        assertEquals("404", c.key());
        assertEquals("HTTP 404", c.label());
    }

    /**
     * errorCode=HTTP_500 归为状态码。
     */
    @Test
    void classifyHttpStatusFromErrorCode() {
        TraceSpan span = new TraceSpan();
        span.setSuccess(false);
        span.setErrorCode("HTTP_500");
        span.setErrorMessage("HTTP Status: 500");
        ErrorSpanClassifier.Classification c = ErrorSpanClassifier.classify(span);
        assertEquals("status", c.kind());
        assertEquals("500", c.key());
    }

    /**
     * 异常信息提取简单类名。
     */
    @Test
    void classifyExceptionSimpleName() {
        TraceSpan span = new TraceSpan();
        span.setSuccess(false);
        span.setErrorCode("EXCEPTION");
        span.setErrorMessage("java.lang.NullPointerException: x was null");
        ErrorSpanClassifier.Classification c = ErrorSpanClassifier.classify(span);
        assertEquals("exception", c.kind());
        assertEquals("NullPointerException", c.key());
    }

    /**
     * 无法识别时归 other。
     */
    @Test
    void classifyOtherUnknown() {
        TraceSpan span = new TraceSpan();
        span.setSuccess(false);
        span.setStatusCode("ERROR");
        ErrorSpanClassifier.Classification c = ErrorSpanClassifier.classify(span);
        assertEquals("other", c.kind());
        assertEquals("UNKNOWN", c.key());
    }
}
