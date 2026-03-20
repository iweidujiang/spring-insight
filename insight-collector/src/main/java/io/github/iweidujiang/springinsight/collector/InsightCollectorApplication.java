package io.github.iweidujiang.springinsight.collector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight Collector 启动类
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Slf4j
@SpringBootApplication(scanBasePackages = {
        "io.github.iweidujiang.springinsight.collector",
        "io.github.iweidujiang.springinsight.storage"
})
public class InsightCollectorApplication {

    public static void main(String[] args) throws UnknownHostException {
        log.info("=========================================");
        log.info("正在启动 Spring Insight Collector...");
        log.info("=========================================");

        Instant startTime = Instant.now();

        ConfigurableApplicationContext context = SpringApplication.run(InsightCollectorApplication.class, args);

        Duration startupDuration = Duration.between(startTime, Instant.now());
        Environment env = context.getEnvironment();

        String appName = env.getProperty("spring.application.name", "spring-insight-collector");
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String hostAddress = InetAddress.getLocalHost().getHostAddress();

        log.info("""
                \n===========================================================
                Spring Insight Collector 启动成功!
                启动耗时: {} 毫秒
                应用名称: {}
                本地地址: http://localhost:{}{}
                外部地址: http://{}:{}{}
                运行环境: {}
                配置文件: {}
                ===========================================================""",
                startupDuration.toMillis(),
                appName,
                port, contextPath,
                hostAddress, port, contextPath,
                env.getActiveProfiles().length > 0 ? String.join(",", env.getActiveProfiles()) : "default",
                env.getProperty("spring.config.name", "application.yml")
        );

        log.info("[Collector] 链路数据为内存存储（与 insight-storage 同进程时由 TraceSpanPersistenceService 持有）");
        log.info("Collector服务已就绪，等待接收Span数据上报...");
    }

}
