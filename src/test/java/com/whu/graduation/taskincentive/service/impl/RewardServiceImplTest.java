package com.whu.graduation.taskincentive.service.impl;

import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.common.enums.StockType;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.mq.RewardProducer;
import com.whu.graduation.taskincentive.strategy.reward.RewardStrategy;
import com.whu.graduation.taskincentive.strategy.stock.StockStrategy;
import com.whu.graduation.taskincentive.testutil.ReflectionTestUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RewardServiceImplTest {

    @Test
    void init_shouldBuildStrategyMaps_andGrantRewardShouldDelegateProducer() throws Exception {
        RewardStrategy rewardStrategy = mock(RewardStrategy.class);
        when(rewardStrategy.getType()).thenReturn(RewardType.POINT);
        StockStrategy stockStrategy = mock(StockStrategy.class);
        when(stockStrategy.getType()).thenReturn(StockType.LIMITED);

        RewardProducer rewardProducer = mock(RewardProducer.class);

        RewardServiceImpl service = new RewardServiceImpl();
        ReflectionTestUtils.setFieldRecursively(service, "rewardStrategyList", List.of(rewardStrategy));
        ReflectionTestUtils.setFieldRecursively(service, "stockStrategyList", List.of(stockStrategy));
        ReflectionTestUtils.setFieldRecursively(service, "rewardProducer", rewardProducer);

        service.init();

        Field rewardStrategiesField = RewardServiceImpl.class.getDeclaredField("rewardStrategies");
        rewardStrategiesField.setAccessible(true);
        Map<?, ?> rewardStrategies = (Map<?, ?>) rewardStrategiesField.get(service);
        assertEquals(1, rewardStrategies.size());
        assertTrue(rewardStrategies.containsKey(RewardType.POINT));

        Field stockStrategiesField = RewardServiceImpl.class.getDeclaredField("stockStrategies");
        stockStrategiesField.setAccessible(true);
        Map<?, ?> stockStrategies = (Map<?, ?>) stockStrategiesField.get(service);
        assertEquals(1, stockStrategies.size());
        assertTrue(stockStrategies.containsKey(StockType.LIMITED));

        Reward reward = Reward.builder().rewardType(RewardType.POINT).amount(10).build();
        assertTrue(service.grantReward(100L, reward));
        verify(rewardProducer).sendReward(100L, reward);
    }
}

