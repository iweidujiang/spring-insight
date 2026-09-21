/**
 * TracingFeignClient 单元测试：remoteService / path 解析。
 *
 * @since：2026-09-07
 * @author：苏渡苇 公众号：苏渡苇
 *
 * GitHub：https://github.com/iweidujiang
 */
package io.github.iweidujiang.springinsight.agent.boot2.feign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TracingFeignClientTest {

    /**
     * 校验从 URL 解析 host 作为 remoteService。
     */
    @Test
    public void resolveRemoteService_usesHost() {
        assertEquals("provider", TracingFeignClient.resolveRemoteService("http://provider/api/hello"));
        assertEquals("unknown", TracingFeignClient.resolveRemoteService("not-a-uri"));
    }

    /**
     * 校验 path 提取。
     */
    @Test
    public void safePath_extractsPath() {
        assertEquals("/api/hello", TracingFeignClient.safePath("http://provider/api/hello"));
    }
}
