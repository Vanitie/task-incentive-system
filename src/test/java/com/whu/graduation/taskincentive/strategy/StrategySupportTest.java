package com.whu.graduation.taskincentive.strategy;

import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.common.enums.StockType;
import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.service.TaskStockService;
import com.whu.graduation.taskincentive.service.UserBadgeService;
import com.whu.graduation.taskincentive.service.UserService;
import com.whu.graduation.taskincentive.strategy.reward.BadgeRewardStrategy;
import com.whu.graduation.taskincentive.strategy.reward.ItemRewardStrategy;
import com.whu.graduation.taskincentive.strategy.reward.PointsRewardStrategy;
import com.whu.graduation.taskincentive.strategy.stock.LimitedStockStrategy;
import com.whu.graduation.taskincentive.strategy.stock.UnlimitedStockStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StrategySupportTest {

    @Test
    public void unlimitedStock_shouldAlwaysPass() {
        UnlimitedStockStrategy strategy = new UnlimitedStockStrategy();
        assertEquals(StockType.UNLIMITED, strategy.getType());
        assertTrue(strategy.acquireStock(1L, 1));
    }

    @Test
    public void limitedStock_shouldFallbackToDb_whenRedisUnavailable() throws Exception {
        LimitedStockStrategy strategy = new LimitedStockStrategy();
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        TaskStockService stockService = mock(TaskStockService.class);

        setField(strategy, "redisTemplate", redisTemplate);
        setField(strategy, "taskStockService", stockService);
        strategy.init();

        when(redisTemplate.execute(any(), anyList())).thenReturn(null);
        when(stockService.deductStock(10L, 1, 1)).thenReturn(true);

        assertTrue(strategy.acquireStock(10L, 1));
    }

    @Test
    public void limitedStock_shouldInitShardAndRetry_whenLuaReturnsMinusOne() throws Exception {
        LimitedStockStrategy strategy = new LimitedStockStrategy();
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        TaskStockService stockService = mock(TaskStockService.class);

        setField(strategy, "redisTemplate", redisTemplate);
        setField(strategy, "taskStockService", stockService);
        strategy.init();

        TaskStock taskStock = new TaskStock();
        taskStock.setAvailableStock(80);

        when(redisTemplate.execute(any(), anyList())).thenReturn(-1L).thenReturn(1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stockService.getByIdAndStageIndex(10L, 1)).thenReturn(taskStock);
        when(valueOperations.setIfAbsent(anyString(), anyString())).thenReturn(true);

        assertTrue(strategy.acquireStock(10L, 1));
    }

    @Test
    public void limitedStock_shouldReturnFalse_whenLuaSaysNoStock() throws Exception {
        LimitedStockStrategy strategy = new LimitedStockStrategy();
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

        setField(strategy, "redisTemplate", redisTemplate);
        setField(strategy, "taskStockService", null);
        strategy.init();

        when(redisTemplate.execute(any(), anyList())).thenReturn(0L);
        assertFalse(strategy.acquireStock(10L, 1));
    }

    @Test
    public void rewardStrategies_shouldReturnExpectedResult() throws Exception {
        PointsRewardStrategy points = new PointsRewardStrategy();
        BadgeRewardStrategy badge = new BadgeRewardStrategy();
        ItemRewardStrategy item = new ItemRewardStrategy();

        UserService userService = mock(UserService.class);
        UserBadgeService userBadgeService = mock(UserBadgeService.class);
        setField(points, "userService", userService);
        setField(badge, "badgeService", userBadgeService);

        Reward reward = Reward.builder().amount(12).code(3001).build();
        when(userService.updateUserPoints(1001L, 12)).thenReturn(true);
        when(userBadgeService.grantBadge(1001L, 3001)).thenReturn(true);

        assertEquals(RewardType.POINT, points.getType());
        assertEquals(RewardType.BADGE, badge.getType());
        assertEquals(RewardType.ITEM, item.getType());

        assertTrue(points.grantReward(1001L, reward));
        assertTrue(badge.grantReward(1001L, reward));
        assertTrue(item.grantReward(1001L, reward));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

