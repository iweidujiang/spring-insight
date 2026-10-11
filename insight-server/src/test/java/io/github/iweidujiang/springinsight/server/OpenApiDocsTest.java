package io.github.iweidujiang.springinsight.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B6：springdoc 暴露只读 OpenAPI 文档。
 *
 * @since 2026-10-11
 * @author 公众号：苏渡苇 GitHub：https://github.com/iweidujiang
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * {@code GET /v3/api-docs} 返回 OpenAPI 3 JSON，并包含上报健康路径。
     */
    @Test
    void apiDocsExposesOpenApiJson() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.openapi").value(containsString("3.")))
                .andExpect(jsonPath("$.info.title").value("Spring Insight Server API"))
                .andExpect(jsonPath("$.paths['/api/v1/health']").exists());
    }
}
