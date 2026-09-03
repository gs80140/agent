package com.example.supportagent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 把参数校验和运行时异常统一转换为 RFC 9457 Problem Details。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        // 对前端返回第一个字段错误，避免暴露 Spring 内部 BindingResult 结构。
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                exception.getBindingResult().getFieldErrors().stream()
                        .findFirst().map(error -> error.getDefaultMessage()).orElse("请求参数不合法"));
        detail.setTitle("请求校验失败");
        return detail;
    }

    /**
     * 静态资源缺失不是 Agent 执行异常，不能落入下面的 500 兜底处理器。
     * Chrome DevTools 会自动探测 appspecific 描述文件；本应用没有需要声明的 DevTools 能力，
     * 因此对该探测明确返回 204。其他未知资源保持语义正确的 404。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> handleMissingResource(NoResourceFoundException exception) {
        String path = exception.getResourcePath().replace('\\', '/');
        if (path.equals(".well-known/appspecific/com.chrome.devtools.json")
                || path.equals("/.well-known/appspecific/com.chrome.devtools.json")) {
            return ResponseEntity.noContent().build();
        }

        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "请求的资源不存在");
        detail.setTitle("资源未找到");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        // 完整异常仅写服务端日志，HTTP 响应不泄露模型、Nacos 或内部实现细节。
        log.error("Agent 请求处理失败", exception);
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "AI 服务暂时不可用，请稍后重试");
        detail.setTitle("Agent 执行失败");
        return detail;
    }
}
