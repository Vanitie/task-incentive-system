package com.whu.graduation.taskincentive.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleOther(Exception ex) {
        // 记录异常栈，便于调试
        ex.printStackTrace();
        ErrorResponse resp = new ErrorResponse(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
        return ResponseEntity.status(500).body(resp);
    }
}
