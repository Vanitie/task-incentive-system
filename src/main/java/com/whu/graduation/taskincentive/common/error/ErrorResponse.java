package com.whu.graduation.taskincentive.common.error;

/**
 * 统一错误响应体
 */
public class ErrorResponse {

    private int code;
    private String message;
    private Object data;

    public ErrorResponse() {}

    public ErrorResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public ErrorResponse(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public Object getData() { return data; }

    public void setCode(int code) { this.code = code; }
    public void setMessage(String message) { this.message = message; }
    public void setData(Object data) { this.data = data; }
}
