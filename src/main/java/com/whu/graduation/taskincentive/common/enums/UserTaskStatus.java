package com.whu.graduation.taskincentive.common.enums;

/**
 * 用户任务状态枚举
 */
public enum UserTaskStatus {
    ACCEPTED(1),
    IN_PROGRESS(2),
    COMPLETED(3),
    CANCELLED(4);

    private final int code;

    UserTaskStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static UserTaskStatus fromCode(Integer code) {
        if (code == null) return null;
        for (UserTaskStatus s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }
}
