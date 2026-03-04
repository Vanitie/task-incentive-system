package com.whu.graduation.taskincentive.event;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户事件
 */
@Data
public class UserEvent {
    private Long userId;
    private String eventType; // USER_LEARN, USER_SIGN
    private Integer value;    // 次数/分钟
    private LocalDateTime time;
}
