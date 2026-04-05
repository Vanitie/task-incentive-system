package com.whu.graduation.taskincentive.strategy.stock;

import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.service.TaskStockService;
import com.whu.graduation.taskincentive.testutil.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LimitedStockStrategyTest {

    private LimitedStockStrategy strategy;
    private RedisTemplate<String, String> redisTemplate;
    private TaskStockService taskStockService;
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        strategy = new LimitedStockStrategy();
        redisTemplate = mock(RedisTemplate.class);
        taskStockService = mock(TaskStockService.class);
        valueOps = mock(ValueOperations.class);

        ReflectionTestUtils.setFieldRecursively(strategy, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setFieldRecursively(strategy, "taskStockService", taskStockService);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        strategy.init();
    }

    @Test
    void acquireStock_shouldReturnTrue_whenLuaResultPositive() {
        when(redisTemplate.execute(any(), anyList())).thenReturn(1L);

        boolean acquired = strategy.acquireStock(100L, 1);

        assertTrue(acquired);
    }

    @Test
    void acquireStock_shouldReturnFalse_whenLuaResultZero() {
        when(redisTemplate.execute(any(), anyList())).thenReturn(0L);

        boolean acquired = strategy.acquireStock(100L, 1);

        assertFalse(acquired);
    }

    @Test
    void acquireStock_shouldFallbackToDb_whenLuaReturnsNull() {
        when(redisTemplate.execute(any(), anyList())).thenReturn(null);
        when(taskStockService.deductStock(anyLong(), anyInt(), anyInt())).thenReturn(true);

        boolean acquired = strategy.acquireStock(101L, 2);

        assertTrue(acquired);
        verify(taskStockService, times(1)).deductStock(101L, 2, 1);
    }

    @Test
    void acquireStock_shouldInitShardAndRetry_whenRedisShardMissing() {
        when(redisTemplate.execute(any(), anyList())).thenReturn(-1L, 1L);
        when(taskStockService.getByIdAndStageIndex(102L, 3))
                .thenReturn(TaskStock.builder().taskId(102L).stageIndex(3).availableStock(16).build());

        boolean acquired = strategy.acquireStock(102L, 3);

        assertTrue(acquired);
        verify(valueOps, times(8)).setIfAbsent(any(), any());
        verify(redisTemplate, times(2)).execute(any(), anyList());
    }

    @Test
    void acquireStock_shouldReturnFalse_whenLuaThrowsAndNoDbService() {
        ReflectionTestUtils.setFieldRecursively(strategy, "taskStockService", null);
        when(redisTemplate.execute(any(), anyList())).thenThrow(new RuntimeException("redis down"));

        boolean acquired = strategy.acquireStock(103L, 1);

        assertFalse(acquired);
    }

    @Test
    void acquireStock_shouldFallbackToDb_whenLuaThrows() {
        when(redisTemplate.execute(any(), anyList())).thenThrow(new RuntimeException("redis down"));
        when(taskStockService.deductStock(104L, 2, 1)).thenReturn(true);

        boolean acquired = strategy.acquireStock(104L, 2);

        assertTrue(acquired);
    }

    @Test
    void acquireStock_shouldReturnFalse_whenShardMissingButDbStockEmpty() {
        when(redisTemplate.execute(any(), anyList())).thenReturn(-1L);
        when(taskStockService.getByIdAndStageIndex(105L, 4))
                .thenReturn(TaskStock.builder().taskId(105L).stageIndex(4).availableStock(0).build());

        boolean acquired = strategy.acquireStock(105L, 4);

        assertFalse(acquired);
        verify(redisTemplate, times(1)).execute(any(), anyList());
    }

    @Test
    void acquireStock_shouldReturnFalse_whenRetryThrowsAfterShardInit() {
        when(redisTemplate.execute(any(), anyList())).thenReturn(-1L).thenThrow(new RuntimeException("retry failed"));
        when(taskStockService.getByIdAndStageIndex(106L, 5))
                .thenReturn(TaskStock.builder().taskId(106L).stageIndex(5).availableStock(8).build());

        boolean acquired = strategy.acquireStock(106L, 5);

        assertFalse(acquired);
        verify(valueOps, times(8)).setIfAbsent(any(), any());
        verify(redisTemplate, times(2)).execute(any(), anyList());
    }
}

