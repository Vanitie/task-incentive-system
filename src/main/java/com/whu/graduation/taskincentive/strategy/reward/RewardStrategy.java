package com.whu.graduation.taskincentive.strategy.reward;

import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.dto.Reward;

public interface RewardStrategy {

    RewardType getType();

    boolean grantReward(Long userId, Reward reward);

}
