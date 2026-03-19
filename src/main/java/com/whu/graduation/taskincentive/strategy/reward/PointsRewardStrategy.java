package com.whu.graduation.taskincentive.strategy.reward;

import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 积分奖励实现
 */
@Slf4j
@Service
public class PointsRewardStrategy implements RewardStrategy {

    @Autowired
    private UserService userService;

    @Override
    public RewardType getType() {
        return RewardType.POINT;
    }

    @Override
    public boolean grantReward(Long userId, Reward reward) {
        return userService.updateUserPoints(userId, reward.getAmount());
    }
}
