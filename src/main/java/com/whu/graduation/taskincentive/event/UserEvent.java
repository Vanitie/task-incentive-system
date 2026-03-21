package com.whu.graduation.taskincentive.event;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户事件
 */
@Data
public class UserEvent {
    /** 请求ID，用于幂等 */
    private String requestId;

    /** 事件ID */
    private String eventId;

    private Long userId;
    private String eventType; // USER_LEARN, USER_SIGN
    private Integer value;    // 次数/分钟
    private LocalDateTime time;

    /** 设备ID */
    private String deviceId;

    /** IP */
    private String ip;

    /** 渠道 */
    private String channel;

    /** 扩展字段 */
    private java.util.Map<String, Object> ext;
}
