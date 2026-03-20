package io.github.iweidujiang.springinsight.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * ┌───────────────────────────────────────────────┐
 * │ 📦 存储模块启动类
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────┘
 */
@Slf4j
@SpringBootApplication
public class InsightStorageApplication {

    public static void main(String[] args) throws UnknownHostException {
        // 记录启动开始
        log.info("Starting Spring Insight Storage Application...");

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        ConfigurableApplicationContext context = SpringApplication.run(InsightStorageApplication.class, args);

        stopWatch.stop();

        Environment env = context.getEnvironment();
        String appName = env.getProperty("spring.application.name", "Spring Insight Storage");
        String port = env.getProperty("server.port", "8082");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String hostAddress = InetAddress.getLocalHost().getHostAddress();

        log.info("""
                \n----------------------------------------------------------
                Application '{}' started successfully!
                Start Time: {}ms
                Local URL:    http://localhost:{}{}
                External URL: http://{}:{}{}
                Trace store:  in-memory (no JDBC)
                Profiles:     {}
                ----------------------------------------------------------""",
                appName,
                stopWatch.getTotalTimeMillis(),
                port, contextPath,
                hostAddress, port, contextPath,
                env.getActiveProfiles().length > 0 ? env.getActiveProfiles() : "default");

        log.info("[存储模块] 链路数据为进程内内存存储，重启后清空");
    }

    // 简单的StopWatch替代类
    static class StopWatch {
        private long startTime;
        private long endTime;

        public void start() {
            this.startTime = System.currentTimeMillis();
        }

        public void stop() {
            this.endTime = System.currentTimeMillis();
        }

        public long getTotalTimeMillis() {
            return endTime - startTime;
        }
    }

}
