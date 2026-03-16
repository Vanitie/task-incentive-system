package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 奖励事件 DTO，用于消息或事件传输，表示用户获得某个奖励
 */
@Data
@AllArgsConstructor
public class RewardEvent {

    /** 用户 ID */
    private Long userId;

    /** 奖励对象 */
    private Reward reward;
}
