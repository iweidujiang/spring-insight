package io.github.iweidujiang.springinsight.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 简单的测试控制器
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/11
 * └───────────────────────────────────────────────
 */
@Controller
@RequestMapping("/ui")
public class TestController {

    @GetMapping("/test")
    public String testPage(Model model) {
        model.addAttribute("pageTitle", "测试页面");
        return "test"; // 这需要创建test.html
    }
}
