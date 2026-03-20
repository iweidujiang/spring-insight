package com.example.test;

import io.github.iweidujiang.springinsight.agent.autoconfigure.EnableSpringInsight;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Insight 测试应用程序
 */
@SpringBootApplication
@EnableSpringInsight(serviceName = "spring-insight-demo")
public class SpringInsightTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringInsightTestApplication.class, args);
    }

}
