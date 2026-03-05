package com.whu.graduation.taskincentive.strategy.reward;

import com.whu.graduation.taskincentive.common.enums.StockType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 不限量策略
 */
@Slf4j
@Service
public class UnlimitedStockStrategy implements StockStrategy {

    @Override
    public StockType getType() {
        return StockType.UNLIMITED;
    }

    @Override
    public boolean acquireStock(Long rewardId) {
        return true;
    }
}
