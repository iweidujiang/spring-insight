package io.github.iweidujiang.springinsight.agent;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 配置解析相关单测。
 */
class InsightAgentApplicationTests {

    /**
     * 验证 {@link InsightProperties#hasServerUrl()} 对空白与有效 URL 的判断。
     */
    @Test
    void hasServerUrlDetectsConfiguredEndpoint() {
        InsightProperties props = new InsightProperties();
        assertFalse(props.hasServerUrl());

        props.setServerUrl("  http://localhost:9966/  ");
        assertTrue(props.hasServerUrl());
        assertTrue(props.normalizeServerUrl().endsWith("9966"));
    }

    /**
     * serviceName 为空时，应从 {@code spring.application.name} 回退。
     */
    @Test
    void resolveServiceNameFallsBackToApplicationName() {
        InsightProperties props = new InsightProperties();
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "sca-order");

        props.resolveServiceNameFromEnvironment(env);
        props.validate();

        assertEquals("sca-order", props.getServiceName());
    }

    /**
     * 显式配置的 service-name 不应被 application.name 覆盖。
     */
    @Test
    void explicitServiceNameWinsOverApplicationName() {
        InsightProperties props = new InsightProperties();
        props.setServiceName("explicit-name");
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "sca-order");

        props.resolveServiceNameFromEnvironment(env);

        assertEquals("explicit-name", props.getServiceName());
    }

    /**
     * 启用且无名时校验应失败。
     */
    @Test
    void validateFailsWhenNoServiceNameResolvable() {
        InsightProperties props = new InsightProperties();
        props.resolveServiceNameFromEnvironment(new MockEnvironment());
        assertThrows(IllegalArgumentException.class, props::validate);
    }
}
