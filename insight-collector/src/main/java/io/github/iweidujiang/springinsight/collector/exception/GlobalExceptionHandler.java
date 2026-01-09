package io.github.iweidujiang.springinsight.collector.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * ┌───────────────────────────────────────────────
 * │ 📦 全局异常处理器
 * │
 * │ 👤 作者：苏渡苇
 * │ 🔗 公众号：苏渡苇
 * │ 💻 GitHub：https://github.com/iweidujiang
 * │
 * | 📅 @since：2026/1/9
 * └───────────────────────────────────────────────
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> response = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "请求参数验证失败",
                request.getRequestURI()
        );
        response.put("errors", errors);

        log.warn("[全局异常处理] 参数验证失败: {} -> {}", request.getRequestURI(), errors);

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理JSON解析异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleJsonParseException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        Map<String, Object> response = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "请求JSON格式错误",
                request.getRequestURI()
        );

        log.warn("[全局异常处理] JSON解析失败: {} -> {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        Map<String, Object> response = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                String.format("参数类型错误: %s 应为 %s 类型",
                        ex.getName(), ex.getRequiredType().getSimpleName()),
                request.getRequestURI()
        );

        log.warn("[全局异常处理] 参数类型不匹配: {} -> {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(CollectorException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(
            CollectorException ex, HttpServletRequest request) {

        Map<String, Object> response = createErrorResponse(
                ex.getStatus(),
                ex.getMessage(),
                request.getRequestURI()
        );

        log.warn("[全局异常处理] 业务异常: {} -> {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, HttpServletRequest request) {

        Map<String, Object> response = createErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "服务器内部错误",
                request.getRequestURI()
        );

        log.error("[全局异常处理] 未捕获异常: {} -> {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(HttpStatus status, String message, String path) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now());
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());
        response.put("message", message);
        response.put("path", path);
        return response;
    }
}

/**
 * Collector业务异常
 */
@Getter
class CollectorException extends RuntimeException {
    private final HttpStatus status;

    public CollectorException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public CollectorException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

}
