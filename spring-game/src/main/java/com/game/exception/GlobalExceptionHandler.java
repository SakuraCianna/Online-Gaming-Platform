package com.game.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        // 记录业务异常日志
        logger.warn("业务异常 [{}] {} - 状态码: {}, 错误信息: {}",
                request.getMethod(),
                request.getRequestURI(),
                e.getCode(),
                e.getMessage());

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", e.getCode());
        result.put("message", e.getMessage());

        HttpStatus status = HttpStatus.valueOf(e.getCode());
        return ResponseEntity.status(status).body(result);
    }

    /**
     * 处理 StackOverflowError（循环引用导致的序列化问题）
     */
    @ExceptionHandler(StackOverflowError.class)
    public ResponseEntity<Map<String, Object>> handleStackOverflowError(StackOverflowError e, HttpServletRequest request) {
        logger.error("⚠️ StackOverflowError发生 [{}] {} - 请检查实体类的循环引用问题",
                request.getMethod(),
                request.getRequestURI());

        // 打印部分堆栈（前20行，避免日志过大）
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace != null && stackTrace.length > 0) {
            logger.error("堆栈信息（前20行）:");
            for (int i = 0; i < Math.min(20, stackTrace.length); i++) {
                logger.error("  at {}", stackTrace[i].toString());
            }
        }

        // 打印请求信息
        logger.error("请求参数: {}", request.getQueryString());
        logger.error("请求IP: {}", request.getRemoteAddr());

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 500);
        result.put("message", "服务器内部错误：可能存在数据循环引用");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointerException(NullPointerException e, HttpServletRequest request) {
        logger.error("空指针异常 [{}] {} - 错误信息: {}",
                request.getMethod(),
                request.getRequestURI(),
                e.getMessage());
        logger.error("堆栈信息:", e);

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 500);
        result.put("message", "服务器内部错误");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * 处理参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        logger.warn("参数异常 [{}] {} - 错误信息: {}",
                request.getMethod(),
                request.getRequestURI(),
                e.getMessage());

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 400);
        result.put("message", "请求参数错误: " + e.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理其他所有异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e, HttpServletRequest request) {
        logger.error("系统异常 [{}] {} - 错误类型: {}, 错误信息: {}",
                request.getMethod(),
                request.getRequestURI(),
                e.getClass().getSimpleName(),
                e.getMessage());
        logger.error("详细堆栈信息:", e);

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 500);
        result.put("message", "服务器内部错误");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}