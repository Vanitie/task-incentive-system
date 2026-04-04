package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dao.mapper.TaskStockMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserRewardRecordServiceImplTest {

    private UserRewardRecordMapper userRewardRecordMapper;
    private UserMapper userMapper;
    private TaskConfigService taskConfigService;
    private UserRewardRecordServiceImpl service;

    @BeforeEach
    public void setUp() {
        userRewardRecordMapper = Mockito.mock(UserRewardRecordMapper.class);
        userMapper = Mockito.mock(UserMapper.class);
        taskConfigService = Mockito.mock(TaskConfigService.class);
        service = new UserRewardRecordServiceImpl(userRewardRecordMapper, userMapper, taskConfigService);

        try {
            Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
            baseMapperField.setAccessible(true);
            baseMapperField.set(service, userRewardRecordMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGetReceivedUsersLast7Days_dailyDistinctCounts() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -6);
        Date firstDay = cal.getTime();

        Calendar endCal = Calendar.getInstance();
        endCal.setTime(todayStart);
        endCal.add(Calendar.DAY_OF_MONTH, 1);
        Date end = endCal.getTime();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> d0 = new HashMap<>();
        d0.put("the_date", sdf.format(firstDay));
        d0.put("cnt", 2);
        rows.add(d0);

        Calendar day2 = Calendar.getInstance();
        day2.setTime(firstDay);
        day2.add(Calendar.DAY_OF_MONTH, 2);
        Map<String, Object> d2 = new HashMap<>();
        d2.put("the_date", sdf.format(day2.getTime()));
        d2.put("cnt", 5);
        rows.add(d2);

        when(userRewardRecordMapper.countDistinctUserIdsGroupByDate(firstDay, end)).thenReturn(rows);

        List<Long> res = service.getReceivedUsersLast7Days();

        List<Long> expected = Arrays.asList(2L, 0L, 5L, 0L, 0L, 0L, 0L);

        assertEquals(expected, res);
    }

    @Test
    public void initRecordIfAbsent_shouldReturnNull_whenMessageIdBlank() {
        assertNull(service.initRecordIfAbsent("  ", 1L, null));
    }

    @Test
    public void initRecordIfAbsent_shouldReturnExisting_whenAlreadyExists() {
        UserRewardRecord existing = new UserRewardRecord();
        existing.setId(1L);
        when(userRewardRecordMapper.selectByMessageId("m-1")).thenReturn(existing);

        UserRewardRecord result = service.initRecordIfAbsent("m-1", 1001L, null);

        assertEquals(1L, result.getId());
        verify(userRewardRecordMapper, never()).insert(any(UserRewardRecord.class));
    }

    @Test
    public void initRecordIfAbsent_shouldReturnDbRecord_whenDuplicateKeyThrown() {
        UserRewardRecord existing = new UserRewardRecord();
        existing.setId(2L);
        when(userRewardRecordMapper.selectByMessageId("m-dup")).thenReturn(null).thenReturn(existing);
        when(userRewardRecordMapper.insert(any(UserRewardRecord.class))).thenThrow(new DuplicateKeyException("dup"));

        Reward reward = Reward.builder().taskId(10L).rewardType(RewardType.POINT).amount(5).build();
        UserRewardRecord result = service.initRecordIfAbsent("m-dup", 1001L, reward);

        assertNotNull(result);
        assertEquals(2L, result.getId());
    }

    @Test
    public void markProcessing_shouldReturnFalse_whenMessageIdBlank() {
        assertFalse(service.markProcessing(" "));
    }

    @Test
    public void markProcessing_shouldUseInitAndFailedAsFromStates() {
        when(userRewardRecordMapper.updateGrantStatusByMessageIdWithFromStatuses("m-process", 0, 3, 1, null)).thenReturn(1);

        boolean ok = service.markProcessing("m-process");

        assertTrue(ok);
        verify(userRewardRecordMapper, times(1))
                .updateGrantStatusByMessageIdWithFromStatuses("m-process", 0, 3, 1, null);
    }

    @Test
    public void markSuccess_shouldTransitionFromProcessingOnly() {
        when(userRewardRecordMapper.updateGrantStatusByMessageIdWithFromStatus("m-success", 1, 2, null)).thenReturn(1);

        boolean ok = service.markSuccess("m-success");

        assertTrue(ok);
        verify(userRewardRecordMapper, times(1))
                .updateGrantStatusByMessageIdWithFromStatus("m-success", 1, 2, null);
    }

    @Test
    public void markSuccess_shouldReturnFalse_whenNoProcessingRecord() {
        when(userRewardRecordMapper.updateGrantStatusByMessageIdWithFromStatus("m-none", 1, 2, null)).thenReturn(0);

        boolean ok = service.markSuccess("m-none");

        assertFalse(ok);
        verify(userRewardRecordMapper, times(1))
                .updateGrantStatusByMessageIdWithFromStatus("m-none", 1, 2, null);
    }

    @Test
    public void markFailedNewTx_shouldTruncateLongReasonTo500() {
        String longReason = "x".repeat(600);
        when(userRewardRecordMapper.updateGrantStatusByMessageIdWithFromStatuses(eq("m-fail"), eq(0), eq(1), eq(3), any(String.class))).thenReturn(1);

        boolean ok = service.markFailedNewTx("m-fail", longReason);

        assertTrue(ok);
        org.mockito.ArgumentCaptor<String> reasonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(userRewardRecordMapper).updateGrantStatusByMessageIdWithFromStatuses(eq("m-fail"), eq(0), eq(1), eq(3), reasonCaptor.capture());
        assertEquals(500, reasonCaptor.getValue().length());
    }
}

class TaskStockServiceImplTest {

    private TaskStockServiceImpl service;
    private TaskStockMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        service = new TaskStockServiceImpl();
        mapper = Mockito.mock(TaskStockMapper.class);

        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(service, mapper);
    }

    @Test
    void deductStock_shouldReturnTrue_whenAffectedRowsPositive() {
        when(mapper.deductStock(10L, 1, 1)).thenReturn(1);

        boolean ok = service.deductStock(10L, 1, 1);

        assertTrue(ok);
        verify(mapper, times(1)).deductStock(10L, 1, 1);
    }

    @Test
    void deductStock_shouldReturnFalse_whenAffectedRowsZero() {
        when(mapper.deductStock(10L, 1, 1)).thenReturn(0);

        boolean ok = service.deductStock(10L, 1, 1);

        assertFalse(ok);
        verify(mapper, times(1)).deductStock(10L, 1, 1);
    }

    @Test
    void getByIdAndStageIndex_shouldReturnMapperResult() {
        TaskStock stock = new TaskStock();
        stock.setTaskId(10L);
        stock.setStageIndex(2);
        when(mapper.selectOne(any())).thenReturn(stock);

        TaskStock result = service.getByIdAndStageIndex(10L, 2);

        assertNotNull(result);
        assertEquals(10L, result.getTaskId());
        assertEquals(2, result.getStageIndex());
        verify(mapper, times(1)).selectOne(any());
    }
}
