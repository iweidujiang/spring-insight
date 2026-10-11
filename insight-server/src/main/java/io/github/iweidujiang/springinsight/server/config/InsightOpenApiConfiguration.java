package io.github.iweidujiang.springinsight.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * springdoc OpenAPI 元信息（只读文档；不改变业务 API）。
 *
 * @since 2026-10-11
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@Configuration
public class InsightOpenApiConfiguration {

    /**
     * @param serverVersion 与 {@code spring.insight.server.version} / 打包过滤对齐
     * @return OpenAPI 根文档
     */
    @Bean
    public OpenAPI insightOpenApi(
            @Value("${spring.insight.server.version:0.0.0}") String serverVersion) {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring Insight Server API")
                        .description("""
                                监测中心只读 OpenAPI 文档（springdoc）。

                                - **上报**：`/api/v1/spans/*`、`/api/v1/health`
                                - **控制台查询**：`/api/v1/ui/**`（设置 / Trace / 拓扑 / 存储等）
                                - **鉴权**：可选 ingest Token、控制台登录（默认关闭）

                                JSON：`GET /v3/api-docs` · UI：`/swagger-ui.html`
                                """)
                        .version(serverVersion)
                        .contact(new Contact()
                                .name("苏渡苇")
                                .url("https://github.com/iweidujiang/spring-insight"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.txt")))
                .servers(List.of(new Server()
                        .url("/")
                        .description("当前 insight-server 实例")));
    }
}
