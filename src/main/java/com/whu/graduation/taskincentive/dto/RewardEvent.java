package com.whu.graduation.taskincentive.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 奖励事件
 */
@Data
@AllArgsConstructor
public class RewardEvent {

    private Long userId;

    private Reward reward;
}
