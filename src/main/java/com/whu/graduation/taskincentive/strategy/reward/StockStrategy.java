package com.whu.graduation.taskincentive.strategy.reward;

import com.whu.graduation.taskincentive.common.enums.StockType;

public interface StockStrategy {

    StockType getType();

    boolean acquireStock(Long rewardId);

}
