package com.whu.graduation.taskincentive.service;

import com.whu.graduation.taskincentive.dto.Reward;

/**
 * 奖励服务接口
 */
public interface RewardService {
    boolean grantReward(Long userId, Reward reward);
}
