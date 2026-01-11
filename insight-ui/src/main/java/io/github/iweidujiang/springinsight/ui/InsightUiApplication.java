package io.github.iweidujiang.springinsight.ui;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 Spring Insight UI 主启动类
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/11
 * └───────────────────────────────────────────────
 */
@Slf4j
@SpringBootApplication(scanBasePackages = {
        "io.github.iweidujiang.springinsight.storage",
        "io.github.iweidujiang.springinsight.ui"
})
public class InsightUiApplication {

    public static void main(String[] args) throws UnknownHostException {
        ConfigurableApplicationContext context = SpringApplication.run(InsightUiApplication.class, args);
        Environment env = context.getEnvironment();
        String appName = env.getProperty("spring.application.name", "spring-insight-api");
        String port = env.getProperty("server.port", "8083");
        String hostAddress = InetAddress.getLocalHost().getHostAddress();
        String contextPath = env.getProperty("server.servlet.context-path", "");
        log.info("""
                        
                        ===============================================
                        {} 启动成功!
                        本地地址: \thttp://localhost:{}{}
                        外部地址: \thttp://{}:{}{}
                        API文档: \thttp://localhost:{}{}/swagger-ui.html
                        ===============================================""",
                appName, port, contextPath,
                hostAddress, port, contextPath,
                port, contextPath);
    }

}
