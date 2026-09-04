package io.github.iweidujiang.springinsight.collector;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证 Collector 适配层（Controller + Service + Storage）可装配。
 */
@SpringBootTest(classes = CollectorTestConfiguration.class)
class InsightCollectorApplicationTests {

    /**
     * 上下文加载成功即通过。
     */
    @Test
    void contextLoads() {
    }
}
