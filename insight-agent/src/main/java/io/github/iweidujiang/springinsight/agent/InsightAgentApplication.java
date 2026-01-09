package io.github.iweidujiang.springinsight.agent;

import io.github.iweidujiang.springinsight.agent.autoconfigure.InsightAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(InsightAutoConfiguration.class)
public class InsightAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsightAgentApplication.class, args);
    }

}


