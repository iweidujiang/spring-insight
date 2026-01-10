package io.github.iweidujiang.springinsight.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 服务与拓扑查询控制器
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/11
 * └───────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "服务与拓扑", description = "服务发现与依赖拓扑查询接口")
public class ServiceTopologyController {
}
