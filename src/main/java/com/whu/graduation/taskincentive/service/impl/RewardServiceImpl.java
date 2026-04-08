package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.common.enums.StockType;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.mq.RewardProducer;
import com.whu.graduation.taskincentive.service.RewardService;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
import com.whu.graduation.taskincentive.strategy.reward.RewardStrategy;
import com.whu.graduation.taskincentive.strategy.stock.StockStrategy;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 奖励服务实现
 */
@Slf4j
@Service
public class RewardServiceImpl implements RewardService {

    @Resource
    private List<RewardStrategy> rewardStrategyList;

    @Resource
    private List<StockStrategy> stockStrategyList;

    private Map<RewardType, RewardStrategy> rewardStrategies;
    private Map<StockType, StockStrategy> stockStrategies;

    @Autowired
    private RewardProducer rewardProducer;

    @Autowired
    private UserRewardRecordService userRewardRecordService;

    @PostConstruct
    public void init() {

        rewardStrategies = rewardStrategyList.stream()
                .collect(Collectors.toMap(
                        RewardStrategy::getType,
                        Function.identity()
                ));

        stockStrategies = stockStrategyList.stream()
                .collect(Collectors.toMap(
                        StockStrategy::getType,
                        Function.identity()
                ));
    }

    /**
     * 发放奖励
     */
    public boolean grantReward(Long userId, Reward reward) {
        rewardProducer.sendReward(userId, reward);
        return true;
    }

    @Override
    public boolean grantRewardDirect(Long userId, Reward reward) {
        if (reward == null || reward.getRewardType() == null) {
            return false;
        }
        RewardStrategy strategy = rewardStrategies.get(reward.getRewardType());
        if (strategy == null) {
            return false;
        }
        if (reward.getRewardId() == null) {
            reward.setRewardId(IdWorker.getId());
        }

        boolean granted = strategy.grantReward(userId, reward);
        if (!granted) {
            return false;
        }

        UserRewardRecord record = UserRewardRecord.builder()
                .id(IdWorker.getId())
                .userId(userId)
                .taskId(reward.getTaskId())
                .rewardType(reward.getRewardType().toString())
                .status(0)
                .messageId("direct-" + UUID.randomUUID())
                .rewardId(reward.getRewardId())
                .grantStatus(2)
                .rewardValue(reward.getAmount())
                .createTime(new Date())
                .updateTime(new Date())
                .build();
        return userRewardRecordService.save(record);
    }
}