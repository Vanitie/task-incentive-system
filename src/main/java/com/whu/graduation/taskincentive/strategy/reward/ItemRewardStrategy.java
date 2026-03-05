package com.whu.graduation.taskincentive.strategy.reward;

import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.dto.Reward;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 实物奖励，因为没有实物，目前是空实现
 */
@Slf4j
@Service
public class ItemRewardStrategy implements RewardStrategy {

//    @Autowired
//    private ItemService itemService;

    @Override
    public RewardType getType() {
        return RewardType.ITEM;
    }

    @Override
    public boolean grantReward(Long userId, Reward reward) {

//        itemService.grantItem(userId, reward.getRewardId());

        return true;
    }
}
