package com.whu.graduation.taskincentive.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.stream.Collectors;

/**
 * 全局异常处理器，统一返回 `ErrorResponse`。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        ErrorCode ec = ex.getErrorCode();
        ErrorResponse resp = new ErrorResponse(ec.getCode(), ex.getMessage());
        return ResponseEntity.status(400).body(resp);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ":" + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ErrorResponse resp = new ErrorResponse(ErrorCode.VALIDATION_ERROR.getCode(), msg);
        return ResponseEntity.badRequest().body(resp);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        String value = ex.getValue() == null ? "null" : String.valueOf(ex.getValue());
        ErrorResponse resp = new ErrorResponse(
                ErrorCode.VALIDATION_ERROR.getCode(),
                String.format("%s format invalid: %s", name, value)
        );
        return ResponseEntity.badRequest().body(resp);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        ErrorResponse resp = new ErrorResponse(401, "未认证或认证已失效");
        return ResponseEntity.status(401).body(resp);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse resp = new ErrorResponse(403, "无权限访问该资源");
        return ResponseEntity.status(403).body(resp);
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleOther(Exception ex) {
        // 记录异常栈，便于调试
        ex.printStackTrace();
        ErrorResponse resp = new ErrorResponse(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
        return ResponseEntity.status(500).body(resp);
    }
}
