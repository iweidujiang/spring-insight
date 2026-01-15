package io.github.iweidujiang.springinsight.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@SpringBootApplication(scanBasePackages = {"io.github.iweidujiang.springinsight"})
@EnableScheduling
public class DemoAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoAppApplication.class, args);
        log.info("Demo应用启动成功，测试接口已就绪");
    }
}
