package io.github.iweidujiang.springinsight.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证 insight-server 应用上下文能够正常启动。
 */
@SpringBootTest
class InsightServerApplicationTests {

    /**
     * 空测试：若 Bean 扫描或依赖缺失，上下文加载会失败。
     */
    @Test
    void contextLoads() {
        // 仅校验 Spring 容器可装配 collector + storage + SPA 控制器
    }
}
