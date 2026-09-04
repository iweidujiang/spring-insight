package io.github.iweidujiang.springinsight.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import io.github.iweidujiang.springinsight.server.config.InsightServerStorageProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;

/**
 * Spring Insight 独立监测中心启动类。
 * <p>
 * 职责：在本进程内组装「收集 API + 存储 + 控制台 UI」，作为微服务场景下的唯一控制台入口。
 * 业务服务只需通过 Agent 向本服务上报 Span，不必再各自嵌入 UI。
 * </p>
 *
 * @author 苏渡苇
 * @since 2026/8/13
 */
@Slf4j
@EnableConfigurationProperties(InsightServerStorageProperties.class)
@SpringBootApplication(scanBasePackages = {
        // 扫描收集器 HTTP 适配层与业务层（上报 / UI 查询 API）
        "io.github.iweidujiang.springinsight.collector",
        // 扫描存储
        "io.github.iweidujiang.springinsight.storage",
        // 扫描本模块（SPA 回退控制器等）
        "io.github.iweidujiang.springinsight.server"
})
public class InsightServerApplication {

    /**
     * 应用入口：启动 Spring Boot 并打印控制台访问地址。
     *
     * @param args 命令行参数，可覆盖 {@code server.port} 等配置
     * @throws UnknownHostException 本机地址解析失败时抛出
     */
    public static void main(String[] args) throws UnknownHostException {
        // 记录启动起点，用于计算启动耗时
        Instant startTime = Instant.now();

        ConfigurableApplicationContext context = SpringApplication.run(InsightServerApplication.class, args);

        Environment env = context.getEnvironment();
        // 应用名：用于启动日志展示，默认 spring-insight-server
        String appName = env.getProperty("spring.application.name", "spring-insight-server");
        // HTTP 端口：默认 9966，避免与常见业务 8080 冲突
        String port = env.getProperty("server.port", "9966");
        // 本机 IP：打印「外部地址」便于局域网访问
        String hostAddress = InetAddress.getLocalHost().getHostAddress();
        // 启动耗时（毫秒）
        long startupMs = Duration.between(startTime, Instant.now()).toMillis();

        log.info("""
                \n===========================================================
                Spring Insight Server 启动成功!
                启动耗时: {} 毫秒
                应用名称: {}
                控制台 UI: http://localhost:{}/
                上报地址: http://localhost:{}/api/v1/spans/batch
                健康检查: http://localhost:{}/api/v1/health
                外部访问: http://{}:{}/
                ===========================================================""",
                startupMs, appName, port, port, port, hostAddress, port);
    }
}
