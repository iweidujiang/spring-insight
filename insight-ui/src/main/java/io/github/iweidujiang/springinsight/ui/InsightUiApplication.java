package io.github.iweidujiang.springinsight.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
		"io.github.iweidujiang.springinsight.ui",
		"io.github.iweidujiang.springinsight.storage"
})
public class InsightUiApplication {

	public static void main(String[] args) {
		SpringApplication.run(InsightUiApplication.class, args);
	}

}
