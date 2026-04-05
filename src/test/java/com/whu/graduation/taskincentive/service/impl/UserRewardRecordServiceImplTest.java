package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.dao.entity.User;
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
import java.lang.reflect.Method;
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
    public void initRecordIfAbsent_shouldReturnNull_whenMessageIdEmpty() {
        assertNull(service.initRecordIfAbsent("", 1L, null));
    }

    @Test
    public void initRecordIfAbsent_shouldReturnNull_whenMessageIdNull() {
        assertNull(service.initRecordIfAbsent(null, 1L, null));
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
    public void initRecordIfAbsent_shouldInsertAndReturnNewRecord_whenFirstSeen() {
        when(userRewardRecordMapper.selectByMessageId("m-new")).thenReturn(null);
        when(userRewardRecordMapper.insert(any(UserRewardRecord.class))).thenReturn(1);

        UserRewardRecord result = service.initRecordIfAbsent("m-new", 1001L, null);

        assertNotNull(result);
        assertEquals("m-new", result.getMessageId());
        assertEquals(1001L, result.getUserId());
        assertNull(result.getRewardType());
        assertEquals(0, result.getGrantStatus());
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
    public void markProcessing_shouldReturnFalse_whenNoRowsUpdated() {
        when(userRewardRecordMapper.updateGrantStatusByMessageIdWithFromStatuses("m-p0", 0, 3, 1, null)).thenReturn(0);

        boolean ok = service.markProcessing("m-p0");

        assertFalse(ok);
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

    @Test
    public void markFailedNewTx_shouldReturnFalse_whenNoRowsUpdated() {
        when(userRewardRecordMapper.updateGrantStatusByMessageIdWithFromStatuses("m-fail-none", 0, 1, 3, "err")).thenReturn(0);

        boolean ok = service.markFailedNewTx("m-fail-none", "err");

        assertFalse(ok);
    }

    @Test
    public void listByConditions_shouldReturnDirectly_whenNoRecords() {
        Page<UserRewardRecord> page = new Page<>(1, 10);
        Page<UserRewardRecord> empty = new Page<>(1, 10);
        empty.setRecords(Collections.emptyList());
        when(userRewardRecordMapper.selectPage(eq(page), any())).thenReturn(empty);

        Page<UserRewardRecord> out = service.listByConditions(page, 1L, 2L, "POINT", 0);

        assertEquals(0, out.getRecords().size());
        verify(userMapper, never()).selectBatchIds(any());
        verify(taskConfigService, never()).getTaskConfigsByIds(any());
    }

    @Test
    public void listByConditions_shouldEnrichUserAndTaskNames() {
        Page<UserRewardRecord> page = new Page<>(1, 10);
        UserRewardRecord r1 = new UserRewardRecord();
        r1.setUserId(10L); r1.setTaskId(100L);
        UserRewardRecord r2 = new UserRewardRecord();
        r2.setUserId(null); r2.setTaskId(200L);
        Page<UserRewardRecord> recordsPage = new Page<>(1, 10);
        recordsPage.setRecords(Arrays.asList(r1, r2));
        when(userRewardRecordMapper.selectPage(eq(page), any())).thenReturn(recordsPage);

        when(userMapper.selectBatchIds(any())).thenReturn(List.of(User.builder().id(10L).username("alice").build()));
        TaskConfig task = new TaskConfig();
        task.setId(100L);
        task.setTaskName("task-a");
        when(taskConfigService.getTaskConfigsByIds(any())).thenReturn(Map.of(100L, task));

        Page<UserRewardRecord> out = service.listByConditions(page, null, null, "", null);

        assertEquals("alice", out.getRecords().get(0).getUserName());
        assertEquals("task-a", out.getRecords().get(0).getTaskName());
        assertNull(out.getRecords().get(1).getUserName());
        assertNull(out.getRecords().get(1).getTaskName());
    }

    @Test
    public void reconcileSummary_shouldUseDefaultLimit_whenInputNotPositive() {
        when(userRewardRecordMapper.countByGrantStatus()).thenReturn(List.of(Map.of("grantStatus", 2, "cnt", 3)));
        when(userRewardRecordMapper.countWithoutMessageId()).thenReturn(1L);
        when(userRewardRecordMapper.findDuplicateMessageIds(20)).thenReturn(List.of(Map.of("messageId", "m1", "cnt", 2)));
        List<UserRewardRecord> abnormal = new ArrayList<>();
        UserRewardRecord abnormalRecord = new UserRewardRecord();
        abnormalRecord.setId(1L);
        abnormal.add(abnormalRecord);
        when(userRewardRecordMapper.findAbnormalRecords(20)).thenReturn(abnormal);

        Map<String, Object> out = service.reconcileSummary(0);

        assertTrue(out.containsKey("statusCount"));
        assertEquals(1L, out.get("withoutMessageId"));
        assertTrue(((Map<?, ?>) out.get("grantStatusRef")).containsKey("2"));
        verify(userRewardRecordMapper).findDuplicateMessageIds(20);
        verify(userRewardRecordMapper).findAbnormalRecords(20);
    }

    @Test
    public void reconcileSummary_shouldUseProvidedLimit() {
        when(userRewardRecordMapper.countByGrantStatus()).thenReturn(Collections.emptyList());
        when(userRewardRecordMapper.countWithoutMessageId()).thenReturn(0L);
        when(userRewardRecordMapper.findDuplicateMessageIds(5)).thenReturn(Collections.emptyList());
        when(userRewardRecordMapper.findAbnormalRecords(5)).thenReturn(Collections.emptyList());

        Map<String, Object> out = service.reconcileSummary(5);

        assertNotNull(out);
        verify(userRewardRecordMapper).findDuplicateMessageIds(5);
        verify(userRewardRecordMapper).findAbnormalRecords(5);
    }

    @Test
    public void previewPointReplayDiff_shouldFilterInvalidRows_andReturnSampleLimit() {
        List<Map<String, Object>> expectedRows = new ArrayList<>();
        expectedRows.add(new HashMap<>(Map.of("userId", 1L, "expectedPoints", 100)));
        expectedRows.add(new HashMap<>(Map.of("userId", "bad", "expectedPoints", 50)));
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(expectedRows);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(User.builder().id(1L).pointBalance(80).build()));

        Map<String, Object> out = service.previewPointReplayDiff(1);

        assertEquals(1, out.get("sampleLimit"));
        assertEquals(1, out.get("totalDiffUsers"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) out.get("samples");
        assertEquals(1, samples.size());
        assertEquals(1L, samples.get(0).get("userId"));
    }

    @Test
    public void previewPointReplayDiff_shouldUseDefaultLimitWhenNonPositive() {
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(Collections.emptyList());

        Map<String, Object> out = service.previewPointReplayDiff(0);

        assertEquals(20, out.get("sampleLimit"));
        assertEquals(0, out.get("totalDiffUsers"));
    }

    @Test
    public void selectByMessageId_shouldReturnNull_whenBlank() {
        assertNull(service.selectByMessageId(" "));
    }

    @Test
    public void selectByMessageId_shouldDelegate_whenValid() {
        UserRewardRecord rec = new UserRewardRecord();
        rec.setId(99L);
        when(userRewardRecordMapper.selectByMessageId("m-99")).thenReturn(rec);

        UserRewardRecord out = service.selectByMessageId("m-99");

        assertNotNull(out);
        assertEquals(99L, out.getId());
    }

    @Test
    public void markSuccess_shouldReturnFalse_whenMessageIdBlank() {
        assertFalse(service.markSuccess("  "));
    }

    @Test
    public void markFailedNewTx_shouldReturnFalse_whenMessageIdBlank() {
        assertFalse(service.markFailedNewTx("  ", "e"));
    }

    @Test
    public void executePointReplayCompensation_shouldReturnFailures_whenUserNotFoundOrUpdateThrows() {
        List<Map<String, Object>> expectedRows = new ArrayList<>();
        expectedRows.add(new HashMap<>(Map.of("userId", 1L, "expectedPoints", 100)));
        expectedRows.add(new HashMap<>(Map.of("userId", 2L, "expectedPoints", 50)));
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(expectedRows);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                User.builder().id(1L).pointBalance(80).build(),
                User.builder().id(2L).pointBalance(10).build()
        ));
        when(userMapper.setUserPointBalance(1L, 100)).thenReturn(0);
        when(userMapper.setUserPointBalance(2L, 50)).thenThrow(new RuntimeException("db error"));

        Map<String, Object> out = service.executePointReplayCompensation();

        assertEquals(0, out.get("updatedUsers"));
        assertEquals(2, out.get("failedUsers"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failed = (List<Map<String, Object>>) out.get("failedSamples");
        assertEquals(2, failed.size());
    }

    @Test
    public void executePointReplayCompensation_shouldUpdateUsers_whenDiffExists() {
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(List.of(
                Map.of("userId", 10L, "expectedPoints", 120)
        ));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                User.builder().id(10L).pointBalance(100).build()
        ));
        when(userMapper.setUserPointBalance(10L, 120)).thenReturn(1);

        Map<String, Object> out = service.executePointReplayCompensation();

        assertEquals(1, out.get("updatedUsers"));
        assertEquals(0, out.get("failedUsers"));
    }

    @Test
    public void executePointReplayCompensation_shouldReturnZero_whenNoDiffs() {
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(Collections.emptyList());

        Map<String, Object> out = service.executePointReplayCompensation();

        assertEquals(0, out.get("updatedUsers"));
        assertEquals(0, out.get("failedUsers"));
    }

    @Test
    public void markFailedNewTx_shouldPassNullReason_whenErrorMsgIsNull() {
        when(userRewardRecordMapper.updateGrantStatusByMessageIdWithFromStatuses("m-null", 0, 1, 3, null)).thenReturn(1);

        boolean ok = service.markFailedNewTx("m-null", null);

        assertTrue(ok);
        verify(userRewardRecordMapper).updateGrantStatusByMessageIdWithFromStatuses("m-null", 0, 1, 3, null);
    }

    @Test
    public void previewPointReplayDiff_shouldReturnZero_whenAllUserIdsInvalid() {
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(List.of(
                Map.of("userId", "bad", "expectedPoints", 100)
        ));

        Map<String, Object> out = service.previewPointReplayDiff(3);

        assertEquals(0, out.get("totalDiffUsers"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) out.get("samples");
        assertTrue(samples.isEmpty());
        verify(userMapper, never()).selectBatchIds(any());
    }

    @Test
    public void executePointReplayCompensation_shouldSkipInvalidRows_andKeepZeroResult() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new HashMap<>(Map.of("userId", "bad", "expectedPoints", 10)));
        rows.add(new HashMap<>(Map.of("userId", 2L, "expectedPoints", "x")));
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(rows);

        Map<String, Object> out = service.executePointReplayCompensation();

        assertEquals(0, out.get("updatedUsers"));
        assertEquals(0, out.get("failedUsers"));
        verify(userMapper, never()).setUserPointBalance(any(), any());
    }

    @Test
    public void listByConditions_shouldSkipBatchEnrichment_whenAllIdsNull() {
        Page<UserRewardRecord> page = new Page<>(1, 10);
        UserRewardRecord r = new UserRewardRecord();
        r.setUserId(null);
        r.setTaskId(null);
        Page<UserRewardRecord> recordsPage = new Page<>(1, 10);
        recordsPage.setRecords(List.of(r));
        when(userRewardRecordMapper.selectPage(eq(page), any())).thenReturn(recordsPage);

        Page<UserRewardRecord> out = service.listByConditions(page, null, null, null, null);

        assertEquals(1, out.getRecords().size());
        assertNull(out.getRecords().get(0).getUserName());
        assertNull(out.getRecords().get(0).getTaskName());
        verify(userMapper, never()).selectBatchIds(any());
        verify(taskConfigService, never()).getTaskConfigsByIds(any());
    }

    @Test
    public void getReceivedUsersLast7Days_shouldIgnoreNonCurrentDateRowUntilMatched() {
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar day3 = Calendar.getInstance();
        day3.setTime(firstDay);
        day3.add(Calendar.DAY_OF_MONTH, 3);

        Map<String, Object> row = new HashMap<>();
        row.put("the_date", sdf.format(day3.getTime()));
        row.put("cnt", 7);
        when(userRewardRecordMapper.countDistinctUserIdsGroupByDate(firstDay, endCal.getTime())).thenReturn(List.of(row));

        List<Long> out = service.getReceivedUsersLast7Days();

        assertEquals(7, out.size());
        assertEquals(0L, out.get(0));
        assertEquals(7L, out.get(3));
    }

    @Test
    public void executePointReplayCompensation_shouldUpdateAndFailMixed_whenPartialRows() {
        List<Map<String, Object>> expectedRows = new ArrayList<>();
        expectedRows.add(new HashMap<>(Map.of("userId", 1L, "expectedPoints", 10)));
        expectedRows.add(new HashMap<>(Map.of("userId", 2L, "expectedPoints", 20)));
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(expectedRows);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                User.builder().id(1L).pointBalance(0).build(),
                User.builder().id(2L).pointBalance(0).build()
        ));
        when(userMapper.setUserPointBalance(1L, 10)).thenReturn(1);
        when(userMapper.setUserPointBalance(2L, 20)).thenReturn(0);

        Map<String, Object> out = service.executePointReplayCompensation();

        assertEquals(1, out.get("updatedUsers"));
        assertEquals(1, out.get("failedUsers"));
    }

    @Test
    public void getReceivedUsersLast7Days_shouldTreatInvalidCntAsZero() {
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, Object> row = new HashMap<>();
        row.put("the_date", sdf.format(firstDay));
        row.put("cnt", "bad");
        when(userRewardRecordMapper.countDistinctUserIdsGroupByDate(firstDay, endCal.getTime())).thenReturn(List.of(row));

        List<Long> out = service.getReceivedUsersLast7Days();

        assertEquals(7, out.size());
        assertEquals(0L, out.get(0));
    }

    @Test
    public void selectByUserIdPage_shouldApplyStatusFilter_whenStatusProvided() {
        Page<UserRewardRecord> page = new Page<>(1, 5);
        Page<UserRewardRecord> result = new Page<>(1, 5);
        result.setRecords(Collections.emptyList());
        when(userRewardRecordMapper.selectPage(eq(page), any())).thenReturn(result);

        Page<UserRewardRecord> out = service.selectByUserIdPage(page, 1001L, 1);

        assertEquals(result, out);
        verify(userRewardRecordMapper).selectPage(eq(page), any());
    }

    @Test
    public void selectByUserIdPage_shouldSkipStatusFilter_whenStatusNull() {
        Page<UserRewardRecord> page = new Page<>(1, 5);
        Page<UserRewardRecord> result = new Page<>(1, 5);
        result.setRecords(Collections.emptyList());
        when(userRewardRecordMapper.selectPage(eq(page), any())).thenReturn(result);

        Page<UserRewardRecord> out = service.selectByUserIdPage(page, 1001L, null);

        assertEquals(result, out);
        verify(userRewardRecordMapper).selectPage(eq(page), any());
    }

    @Test
    public void listByConditions_shouldReturnDirectly_whenRecordsNull() {
        Page<UserRewardRecord> page = new Page<>(1, 10);
        Page<UserRewardRecord> nullRecordsPage = new Page<>(1, 10);
        nullRecordsPage.setRecords(null);
        when(userRewardRecordMapper.selectPage(eq(page), any())).thenReturn(nullRecordsPage);

        Page<UserRewardRecord> out = service.listByConditions(page, null, null, null, null);

        assertNull(out.getRecords());
        verify(userMapper, never()).selectBatchIds(any());
        verify(taskConfigService, never()).getTaskConfigsByIds(any());
    }

    @Test
    public void getReceivedUsersLast7Days_shouldSkipRow_whenDateFieldIsNull() {
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

        Map<String, Object> row = new HashMap<>();
        row.put("the_date", null);
        row.put("cnt", 3);
        when(userRewardRecordMapper.countDistinctUserIdsGroupByDate(firstDay, endCal.getTime())).thenReturn(List.of(row));

        List<Long> out = service.getReceivedUsersLast7Days();

        assertEquals(7, out.size());
        assertEquals(0L, out.get(0));
    }

    @Test
    public void selectByMessageId_shouldReturnNull_whenInputIsNull() {
        assertNull(service.selectByMessageId(null));
    }

    @Test
    public void initRecordIfAbsent_shouldHandleRewardTypeNull_whenRewardPresent() {
        when(userRewardRecordMapper.selectByMessageId("m-rt-null")).thenReturn(null);
        when(userRewardRecordMapper.insert(any(UserRewardRecord.class))).thenReturn(1);

        Reward reward = Reward.builder().taskId(10L).rewardType(null).amount(9).rewardId(999L).build();
        UserRewardRecord out = service.initRecordIfAbsent("m-rt-null", 1001L, reward);

        assertNotNull(out);
        assertNull(out.getRewardType());
        assertEquals(9, out.getRewardValue());
    }

    @Test
    public void markProcessingAndSuccessAndFailed_shouldReturnFalse_whenInputIsNull() {
        assertFalse(service.markProcessing(null));
        assertFalse(service.markSuccess(null));
        assertFalse(service.markFailedNewTx(null, "err"));
    }

    @Test
    public void previewPointReplayDiff_shouldReturnZero_whenMapperReturnsNull() {
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(null);

        Map<String, Object> out = service.previewPointReplayDiff(2);

        assertEquals(0, out.get("totalDiffUsers"));
    }

    @Test
    public void previewPointReplayDiff_shouldParseStringUserIdAndExpectedPoints() {
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(List.of(
                new HashMap<>(Map.of("userId", "12", "expectedPoints", "30"))
        ));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(User.builder().id(12L).pointBalance(20).build()));

        Map<String, Object> out = service.previewPointReplayDiff(5);

        assertEquals(1, out.get("totalDiffUsers"));
    }

    @Test
    public void previewPointReplayDiff_shouldTreatNullCurrentBalanceAsZero() {
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(List.of(
                new HashMap<>(Map.of("userId", 1L, "expectedPoints", 1))
        ));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(User.builder().id(1L).pointBalance(null).build()));

        Map<String, Object> out = service.previewPointReplayDiff(5);

        assertEquals(1, out.get("totalDiffUsers"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) out.get("samples");
        assertEquals(-1, samples.get(0).get("delta"));
    }

    @Test
    public void executePointReplayCompensation_shouldSkipWhenCurrentEqualsExpected() {
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(List.of(
                new HashMap<>(Map.of("userId", 10L, "expectedPoints", 100))
        ));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(User.builder().id(10L).pointBalance(100).build()));

        Map<String, Object> out = service.executePointReplayCompensation();

        assertEquals(0, out.get("updatedUsers"));
        verify(userMapper, never()).setUserPointBalance(any(), any());
    }

    @Test
    public void countUsersReceivedToday_shouldDelegateToMapper() {
        when(userRewardRecordMapper.countDistinctUsersReceivedToday()).thenReturn(7L);

        long out = service.countUsersReceivedToday();

        assertEquals(7L, out);
    }

    @Test
    public void parseHelpers_shouldReturnNull_whenInputNull() throws Exception {
        Method parseLong = UserRewardRecordServiceImpl.class.getDeclaredMethod("parseLong", Object.class);
        parseLong.setAccessible(true);
        Method parseInt = UserRewardRecordServiceImpl.class.getDeclaredMethod("parseInt", Object.class);
        parseInt.setAccessible(true);

        assertNull(parseLong.invoke(service, new Object[]{null}));
        assertNull(parseInt.invoke(service, new Object[]{null}));
    }

    @Test
    public void previewPointReplayDiff_shouldBuildDeltaWhenCurrentMissing() {
        List<Map<String, Object>> expectedRows = new ArrayList<>();
        expectedRows.add(new HashMap<>(Map.of("userId", 88L, "expectedPoints", 30)));
        when(userRewardRecordMapper.sumSuccessPointRewardsByUser()).thenReturn(expectedRows);
        when(userMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

        Map<String, Object> out = service.previewPointReplayDiff(3);

        assertEquals(1, out.get("totalDiffUsers"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) out.get("samples");
        assertEquals(-30, samples.get(0).get("delta"));
        assertNull(samples.get(0).get("currentPoints"));
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
