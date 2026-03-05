package com.whu.graduation.taskincentive.service.impl;

import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.common.enums.StockType;
import com.whu.graduation.taskincentive.common.error.BusinessException;
import com.whu.graduation.taskincentive.common.error.ErrorCode;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.mq.RewardProducer;
import com.whu.graduation.taskincentive.service.RewardService;
import com.whu.graduation.taskincentive.strategy.reward.RewardStrategy;
import com.whu.graduation.taskincentive.strategy.reward.StockStrategy;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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

        StockStrategy stockStrategy = stockStrategies.get(reward.getStockType());
        if(stockStrategy == null){
            log.error("未知库存类型 {}", reward.getStockType());
            throw new BusinessException(ErrorCode.UNKNOWN_STOCK_TYPE);
        }
        boolean success = stockStrategy.acquireStock(reward.getRewardId());

        if (!success) {
            log.warn("库存不足 rewardCode={}", reward.getCode());
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
        }
        rewardProducer.sendReward(userId, reward);
        return true;
    }
}