package io.github.iweidujiang.springinsight.ui.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 全局异常处理
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/14
 * └───────────────────────────────────────────────
 */
@Slf4j
@Controller
@ControllerAdvice
public class GlobalExceptionHandler implements ErrorController {
    private final ErrorAttributes errorAttributes;

    public GlobalExceptionHandler(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public String handleAllExceptions(Exception ex, HttpServletRequest request, Model model) {
        log.error("未捕获的异常: {}", request.getRequestURI(), ex);

        model.addAttribute("timestamp", Instant.now().toString());
        model.addAttribute("error", "服务器错误");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        model.addAttribute("path", request.getRequestURI());

        // 开发环境显示堆栈跟踪
        if (isDevelopment()) {
            model.addAttribute("trace", getStackTrace(ex));
        }

        return "error";
    }

    /**
     * 处理404错误
     */
    @RequestMapping("/error")
    public String handleError(WebRequest webRequest, Model model) {
        Map<String, Object> errorAttributes = getErrorAttributes(webRequest);

        Integer status = (Integer) errorAttributes.get("status");
        String error = (String) errorAttributes.get("error");
        String message = (String) errorAttributes.get("message");
        String path = (String) errorAttributes.get("path");

        log.error("HTTP错误 {}: {} - {}", status, error, path);

        model.addAttribute("timestamp", errorAttributes.get("timestamp"));
        model.addAttribute("error", error);
        model.addAttribute("message", message);
        model.addAttribute("status", status);
        model.addAttribute("path", path);

        // 根据状态码显示不同的错误页面
        if (status == HttpStatus.NOT_FOUND.value()) {
            model.addAttribute("error", "页面未找到");
            model.addAttribute("message", "请求的页面不存在或已被移除");
        } else if (status == HttpStatus.FORBIDDEN.value()) {
            model.addAttribute("error", "访问被拒绝");
            model.addAttribute("message", "您没有权限访问此页面");
        } else if (status == HttpStatus.UNAUTHORIZED.value()) {
            model.addAttribute("error", "未授权");
            model.addAttribute("message", "请先登录后再访问此页面");
        }

        return "error";
    }

    private Map<String, Object> getErrorAttributes(WebRequest webRequest) {
        return errorAttributes.getErrorAttributes(
                webRequest,
                ErrorAttributeOptions.of(
                        ErrorAttributeOptions.Include.MESSAGE,
                        ErrorAttributeOptions.Include.STATUS,
                        ErrorAttributeOptions.Include.ERROR,
                        ErrorAttributeOptions.Include.PATH
                )
        );
    }

    private boolean isDevelopment() {
        return "dev".equals(System.getProperty("spring.profiles.active")) ||
                "development".equals(System.getProperty("spring.profiles.active"));
    }

    private String getStackTrace(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
