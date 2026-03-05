package com.whu.graduation.taskincentive.common.error;

/**
 * 全局错误码定义
 */
public enum ErrorCode {
    SUCCESS(0, "成功"),

    // 业务错误 1000-1999
    TASK_NOT_FOUND(1001, "任务不存在"),
    TASK_NOT_ENABLED(1002, "任务未启用"),
    TASK_NOT_STARTED(1003, "任务未开始"),
    TASK_ALREADY_ENDED(1004, "任务已结束"),
    USER_ALREADY_ACCEPTED(1005, "用户已接取该任务"),

    STOCK_INSUFFICIENT(1101, "库存不足"),
    UNKNOWN_STOCK_TYPE(1102, "未知库存类型"),

    UNKNOWN_REWARD_TYPE(1201, "未知奖励类型"),

    MQ_DUPLICATE(1301, "消息已被处理（幂等）"),

    VALIDATION_ERROR(1401, "参数校验失败"),

    INTERNAL_ERROR(5000, "内部错误，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
