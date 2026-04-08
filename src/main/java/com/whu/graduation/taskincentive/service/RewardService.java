package com.whu.graduation.taskincentive.service;

import com.whu.graduation.taskincentive.dto.Reward;

/**
 * 奖励服务接口
 */
public interface RewardService {
    boolean grantReward(Long userId, Reward reward);

    /**
     * 对照链路奖励发放：不经 Kafka，主线程直接执行业务并落库。
     */
    boolean grantRewardDirect(Long userId, Reward reward);
}
