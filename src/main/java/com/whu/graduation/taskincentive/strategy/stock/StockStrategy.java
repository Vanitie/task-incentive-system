package com.whu.graduation.taskincentive.strategy.stock;

import com.whu.graduation.taskincentive.common.enums.StockType;

public interface StockStrategy {

    StockType getType();

    /**
     * 按任务ID扣减库存，限量任务返回 true 表示库存足够并成功扣减，非限量任务直接返回 true
     */
    boolean acquireStock(Long taskId, Integer stageIndex);
}
