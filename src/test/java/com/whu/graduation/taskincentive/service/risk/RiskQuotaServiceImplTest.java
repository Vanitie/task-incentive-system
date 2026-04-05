package com.whu.graduation.taskincentive.service.risk;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.common.error.BusinessException;
import com.whu.graduation.taskincentive.dao.entity.RiskQuota;
import com.whu.graduation.taskincentive.dao.mapper.RiskQuotaMapper;
import com.whu.graduation.taskincentive.dto.risk.RiskQuotaRequest;
import com.whu.graduation.taskincentive.service.risk.impl.RiskQuotaServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskQuotaServiceImplTest {

    @Test
    void page_shouldFillUsedValueAndResetAt() {
        RiskQuotaMapper mapper = mock(RiskQuotaMapper.class);
        RiskCacheStore cacheStore = mock(RiskCacheStore.class);
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        RiskQuotaServiceImpl service = new RiskQuotaServiceImpl(mapper, cacheStore, redisTemplate);

        RiskQuota quota = RiskQuota.builder()
                .id(1L)
                .scopeType("USER")
                .scopeId("1")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("DAY")
                .build();
        Page<RiskQuota> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(quota));
        when(mapper.selectPage(any(), any())).thenReturn(page);
        when(valueOps.multiGet(any())).thenReturn(Collections.singletonList("3"));

        Page<RiskQuota> result = service.page(new Page<>(1, 10));

        assertEquals(3, result.getRecords().get(0).getUsedValue());
        assertNotNull(result.getRecords().get(0).getResetAt());
    }

    @Test
    void createUpdateDelete_shouldWorkAndRefreshCache() {
        RiskQuotaMapper mapper = mock(RiskQuotaMapper.class);
        RiskCacheStore cacheStore = mock(RiskCacheStore.class);
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        RiskQuotaServiceImpl service = new RiskQuotaServiceImpl(mapper, cacheStore, redisTemplate);

        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(RiskQuota.builder().id(1L).build()));
        when(mapper.deleteById(1L)).thenReturn(1);

        RiskQuotaRequest request = RiskQuotaRequest.builder()
                .quotaName("daily user")
                .scopeType("USER")
                .scopeId("u1")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("DAY")
                .limitValue(5)
                .build();

        RiskQuota created = service.create(request);
        assertNotNull(created.getId());

        RiskQuota stored = RiskQuota.builder().id(created.getId()).build();
        when(mapper.selectById(created.getId())).thenReturn(stored);
        request.setId(created.getId());
        RiskQuota updated = service.update(request);
        assertEquals(created.getId(), updated.getId());

        assertEquals(true, service.deleteById(1L));
        verify(cacheStore, times(3)).refreshQuotas(any());
    }

    @Test
    void updateAndDelete_shouldThrowForInvalidInput() {
        RiskQuotaMapper mapper = mock(RiskQuotaMapper.class);
        RiskCacheStore cacheStore = mock(RiskCacheStore.class);
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

        RiskQuotaServiceImpl service = new RiskQuotaServiceImpl(mapper, cacheStore, redisTemplate);

        assertThrows(BusinessException.class, () -> service.deleteById(null));

        RiskQuotaRequest request = RiskQuotaRequest.builder().build();
        assertThrows(BusinessException.class, () -> service.update(request));

        request.setId(99L);
        when(mapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.update(request));
    }

    @Test
    void createAndUpdate_shouldThrowWhenDuplicateExists() {
        RiskQuotaMapper mapper = mock(RiskQuotaMapper.class);
        RiskCacheStore cacheStore = mock(RiskCacheStore.class);
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        RiskQuotaServiceImpl service = new RiskQuotaServiceImpl(mapper, cacheStore, redisTemplate);

        RiskQuotaRequest request = RiskQuotaRequest.builder()
                .id(2L)
                .scopeType("USER")
                .scopeId("u1")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("DAY")
                .build();

        when(mapper.selectById(2L)).thenReturn(RiskQuota.builder().id(2L).build());
        when(mapper.selectOne(any())).thenReturn(RiskQuota.builder().id(3L).build());

        assertThrows(BusinessException.class, () -> service.create(request));
        assertThrows(BusinessException.class, () -> service.update(request));
    }

    @Test
    void page_shouldFallbackToZero_whenMultiGetThrows() {
        RiskQuotaMapper mapper = mock(RiskQuotaMapper.class);
        RiskCacheStore cacheStore = mock(RiskCacheStore.class);
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        RiskQuotaServiceImpl service = new RiskQuotaServiceImpl(mapper, cacheStore, redisTemplate);

        RiskQuota quota = RiskQuota.builder().id(1L).periodType("DAY").build();
        Page<RiskQuota> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(quota));
        when(mapper.selectPage(any(), any())).thenReturn(page);
        when(valueOps.multiGet(any())).thenThrow(new RuntimeException("redis down"));

        Page<RiskQuota> result = service.page(new Page<>(1, 10));

        assertEquals(0, result.getRecords().get(0).getUsedValue());
        assertNotNull(result.getRecords().get(0).getResetAt());
    }

    @Test
    void page_shouldHandleMinuteHourAndInvalidCacheValue() {
        RiskQuotaMapper mapper = mock(RiskQuotaMapper.class);
        RiskCacheStore cacheStore = mock(RiskCacheStore.class);
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        RiskQuotaServiceImpl service = new RiskQuotaServiceImpl(mapper, cacheStore, redisTemplate);

        RiskQuota minuteQuota = RiskQuota.builder().id(1L).periodType("MINUTE").build();
        RiskQuota hourQuota = RiskQuota.builder().id(2L).periodType("HOUR").build();
        Page<RiskQuota> page = new Page<>(1, 10);
        page.setRecords(List.of(minuteQuota, hourQuota));
        when(mapper.selectPage(any(), any())).thenReturn(page);
        when(valueOps.multiGet(any())).thenReturn(List.of("x", "7"));

        Page<RiskQuota> result = service.page(new Page<>(1, 10));

        assertEquals(0, result.getRecords().get(0).getUsedValue());
        assertEquals(7, result.getRecords().get(1).getUsedValue());
        assertNotNull(result.getRecords().get(0).getResetAt());
        assertNotNull(result.getRecords().get(1).getResetAt());
    }

    @Test
    void update_shouldAllowSameIdDuplicateAndDeleteShouldReturnFalseWhenNoRowDeleted() {
        RiskQuotaMapper mapper = mock(RiskQuotaMapper.class);
        RiskCacheStore cacheStore = mock(RiskCacheStore.class);
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        RedisTemplate<String, String> spyRedis = redisTemplate;
        RiskQuotaServiceImpl service = new RiskQuotaServiceImpl(mapper, cacheStore, spyRedis);

        RiskQuota existing = RiskQuota.builder().id(2L).build();
        when(mapper.selectById(2L)).thenReturn(existing);
        when(mapper.selectOne(any())).thenReturn(RiskQuota.builder().id(2L).build());
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        when(mapper.deleteById(2L)).thenReturn(0);

        RiskQuotaRequest request = RiskQuotaRequest.builder()
                .id(2L)
                .scopeType("USER")
                .scopeId("u1")
                .resourceType("ALL")
                .resourceId("ALL")
                .periodType("DAY")
                .limitValue(10)
                .build();

        RiskQuota updated = service.update(request);

        assertEquals(2L, updated.getId());
        assertTrue(updated.getResetAt() != null);
        assertEquals(false, service.deleteById(2L));
    }
}
