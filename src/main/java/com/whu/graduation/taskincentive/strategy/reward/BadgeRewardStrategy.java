package com.whu.graduation.taskincentive.strategy.reward;

import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.service.UserBadgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 徽章奖励
 */
@Slf4j
@Service
public class BadgeRewardStrategy implements RewardStrategy {

    @Autowired
    private UserBadgeService badgeService;

    @Override
    public RewardType getType() {
        return RewardType.BADGE;
    }

    @Override
    public boolean grantReward(Long userId, Reward reward) {
        return badgeService.grantBadge(userId, reward.getCode());
    }
}
