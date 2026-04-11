
import com.whu.graduation.taskincentive.common.error.BusinessException;
import com.whu.graduation.taskincentive.common.enums.UserTaskStatus;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import com.whu.graduation.taskincentive.service.impl.UserTaskInstanceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserTaskInstanceServiceImplTest {

    private UserTaskInstanceServiceImpl service;
    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOps;
    private UserTaskInstanceMapper mapper;
    private TaskConfigService taskConfigService;

    @BeforeEach
    public void setup() throws Exception {
        service = new UserTaskInstanceServiceImpl(null);

        //noinspection unchecked
        redisTemplate = mock(RedisTemplate.class);
        //noinspection unchecked
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        mapper = mock(UserTaskInstanceMapper.class);
        taskConfigService = mock(TaskConfigService.class);

        // ensure baseMapper field is set to mocked mapper for operations like updateWithVersion
        java.lang.reflect.Field fBase = service.getClass().getSuperclass().getDeclaredField("baseMapper");
        fBase.setAccessible(true);
        fBase.set(service, mapper);

        // set the private final userTaskInstanceMapper field to the mock to avoid NPE in methods that use it
        java.lang.reflect.Field fMapperField = UserTaskInstanceServiceImpl.class.getDeclaredField("userTaskInstanceMapper");
        fMapperField.setAccessible(true);
        fMapperField.set(service, mapper);

        java.lang.reflect.Field fRedis = UserTaskInstanceServiceImpl.class.getDeclaredField("redisTemplate");
        fRedis.setAccessible(true);
        fRedis.set(service, redisTemplate);

        java.lang.reflect.Field fKafka = UserTaskInstanceServiceImpl.class.getDeclaredField("kafkaTemplate");
        fKafka.setAccessible(true);
        fKafka.set(service, null); // not used in these tests

        java.lang.reflect.Field fTask = UserTaskInstanceServiceImpl.class.getDeclaredField("taskConfigService");
        fTask.setAccessible(true);
        fTask.set(service, taskConfigService);
    }

    @Test
    public void getAcceptedInstance_returnsNull_whenNoCacheAndNoDb() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(mapper.selectByUserAndTask(1L, 2L)).thenReturn(null);

        assertNull(service.getAcceptedInstance(1L, 2L));
    }

    @Test
    public void directMethods_shouldUseDbOnly() {
        UserTaskInstance row = new UserTaskInstance();
        row.setUserId(500L);
        row.setTaskId(600L);
        row.setStatus(1);
        when(mapper.selectByUserId(500L)).thenReturn(List.of(row));
        when(mapper.updateWithVersion(any(UserTaskInstance.class))).thenReturn(1);

        List<UserTaskInstance> out = service.selectByUserIdDirect(500L);
        assertEquals(1, out.size());

        int updated = service.updateDirect(row);
        assertEquals(1, updated);

        verify(mapper, times(1)).selectByUserId(500L);
        verify(mapper, times(1)).updateWithVersion(row);
        verify(valueOps, never()).set(anyString(), anyString());
    }

    @Test
    public void getAcceptedInstance_readsFromCache_whenCached_andAccepted() {
        UserTaskInstance cached = new UserTaskInstance();
        cached.setUserId(1L);
        cached.setTaskId(2L);
        cached.setStatus(1);
        when(valueOps.get(anyString())).thenReturn(JSON.toJSONString(cached));

        UserTaskInstance res = service.getAcceptedInstance(1L, 2L);
        assertNotNull(res);
        assertEquals(1, res.getStatus());
    }

    @Test
    public void getAcceptedInstance_returnsNull_whenCachedButNotAccepted() {
        UserTaskInstance cached = new UserTaskInstance();
        cached.setUserId(1L);
        cached.setTaskId(2L);
        cached.setStatus(0);
        when(valueOps.get(anyString())).thenReturn(JSON.toJSONString(cached));

        UserTaskInstance res = service.getAcceptedInstance(1L, 2L);

        assertNull(res);
        verify(mapper, never()).selectByUserAndTask(anyLong(), anyLong());
    }

    @Test
    public void acceptTask_throws_whenTaskNotFound() {
        when(taskConfigService.getTaskConfig(10L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.acceptTask(1L, 10L));
    }

    @Test
    public void acceptTask_createsNewInstance_whenNotExist_andTaskEnabled() {
        TaskConfig cfg = new TaskConfig();
        cfg.setId(20L);
        cfg.setStatus(1);
        cfg.setStartTime(new Date(System.currentTimeMillis() - 1000));
        cfg.setEndTime(new Date(System.currentTimeMillis() + 100000));
        when(taskConfigService.getTaskConfig(20L)).thenReturn(cfg);
        when(mapper.selectByUserAndTask(1L, 20L)).thenReturn(null);
        when(mapper.insert(any())).thenReturn(1);

        UserTaskInstance res = service.acceptTask(1L, 20L);
        assertNotNull(res);
        assertEquals(1L, res.getUserId());
        assertEquals(20L, res.getTaskId());
        verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    public void acceptTask_returnsExisting_whenInstanceAlreadyAccepted() {
        UserTaskInstance existing = new UserTaskInstance();
        existing.setUserId(5L);
        existing.setTaskId(6L);
        existing.setStatus(1);
        when(taskConfigService.getTaskConfig(6L)).thenReturn(new TaskConfig() {{ setId(6L); setStatus(1); }});
        when(mapper.selectByUserAndTask(5L,6L)).thenReturn(existing);

        UserTaskInstance res = service.acceptTask(5L, 6L);
        assertNotNull(res);
        assertEquals(6L, res.getTaskId());
        // should not attempt to insert
        verify(mapper, never()).insert(any());
    }

    @Test
    public void acceptTask_shouldPromotePendingInstanceToAccepted() {
        UserTaskInstance existing = new UserTaskInstance();
        existing.setUserId(5L);
        existing.setTaskId(7L);
        existing.setStatus(0);
        TaskConfig cfg = new TaskConfig();
        cfg.setId(7L);
        cfg.setStatus(1);
        cfg.setStartTime(new Date(System.currentTimeMillis() - 1000));
        cfg.setEndTime(new Date(System.currentTimeMillis() + 100000));
        when(taskConfigService.getTaskConfig(7L)).thenReturn(cfg);
        when(mapper.selectByUserAndTask(5L, 7L)).thenReturn(existing);
        when(mapper.updateById(any())).thenReturn(1);

        UserTaskInstance out = service.acceptTask(5L, 7L);

        assertNotNull(out);
        assertEquals(UserTaskStatus.ACCEPTED.getCode(), out.getStatus());
        verify(mapper, times(1)).updateById(any());
        verify(valueOps, atLeastOnce()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    public void acceptTask_throws_whenTaskNotEnabled_statusNotOne() {
        TaskConfig cfg = new TaskConfig(); cfg.setId(30L); cfg.setStatus(0);
        when(taskConfigService.getTaskConfig(30L)).thenReturn(cfg);
        assertThrows(BusinessException.class, () -> service.acceptTask(2L, 30L));
    }

    @Test
    public void update_writesCache_whenStatusPositive() {
        UserTaskInstance inst = new UserTaskInstance();
        inst.setId(100L); inst.setUserId(10L); inst.setTaskId(20L); inst.setStatus(1);
        when(mapper.updateById(any())).thenReturn(1);

        boolean updated = service.update(inst);
        // update returns boolean but we mocked mapper; ensure it triggers redis set in non-transaction path
        verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    public void update_shouldReturnFalse_whenUpdateByIdFailed() {
        UserTaskInstance inst = new UserTaskInstance();
        inst.setId(101L);
        inst.setUserId(10L);
        inst.setTaskId(20L);
        inst.setStatus(1);
        when(mapper.updateById(any())).thenReturn(0);

        boolean updated = service.update(inst);

        assertFalse(updated);
        verify(valueOps, never()).set(anyString(), anyString());
    }

    @Test
    public void update_shouldSkipCacheWrite_whenStatusNotAccepted() {
        UserTaskInstance inst = new UserTaskInstance();
        inst.setId(102L);
        inst.setUserId(10L);
        inst.setTaskId(21L);
        inst.setStatus(0);
        when(mapper.updateById(any())).thenReturn(1);

        boolean updated = service.update(inst);

        assertTrue(updated);
        verify(valueOps, never()).set(anyString(), anyString());
    }

    @Test
    public void update_shouldWriteAfterCommit_whenTransactionActive() {
        UserTaskInstance inst = new UserTaskInstance();
        inst.setId(103L);
        inst.setUserId(10L);
        inst.setTaskId(22L);
        inst.setStatus(1);
        when(mapper.updateById(any())).thenReturn(1);

        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        TransactionSynchronizationManager.initSynchronization();
        try {
            boolean updated = service.update(inst);
            assertTrue(updated);
            verify(valueOps, never()).set(anyString(), anyString());

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(setOps, times(1)).add(anyString(), anyString());
            verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void getOrCreateWithCache_shouldFallbackToDb_whenCachedJsonInvalid() {
        when(valueOps.get(anyString())).thenReturn("{bad-json");
        when(mapper.selectByUserAndTask(12L, 34L)).thenReturn(null).thenReturn(new UserTaskInstance() {{
            setId(909L); setUserId(12L); setTaskId(34L); setStatus(0);
        }});
        when(mapper.insert(any())).thenThrow(new RuntimeException("dup"));

        UserTaskInstance out = service.getOrCreateWithCache(12L, 34L);

        assertNotNull(out);
        assertEquals(12L, out.getUserId());
        assertEquals(34L, out.getTaskId());
    }

    @Test
    public void getOrCreateWithCache_shouldWriteAfterCommit_whenTransactionActive() {
        when(valueOps.get(anyString())).thenReturn(null);
        UserTaskInstance db = new UserTaskInstance();
        db.setId(910L);
        db.setUserId(13L);
        db.setTaskId(35L);
        db.setStatus(0);
        when(mapper.selectByUserAndTask(13L, 35L)).thenReturn(db);

        TransactionSynchronizationManager.initSynchronization();
        try {
            UserTaskInstance out = service.getOrCreateWithCache(13L, 35L);
            assertNotNull(out);
            verify(valueOps, never()).set(anyString(), anyString());

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void updateAndPublish_shouldDeferRedisAndKafka_whenTransactionActive() throws Exception {
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka = mock(org.springframework.kafka.core.KafkaTemplate.class);
        java.lang.reflect.Field fKafka = UserTaskInstanceServiceImpl.class.getDeclaredField("kafkaTemplate");
        fKafka.setAccessible(true);
        fKafka.set(service, kafka);

        UserTaskInstance inst = new UserTaskInstance();
        inst.setId(911L);
        inst.setUserId(42L);
        inst.setTaskId(99L);
        inst.setStatus(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateAndPublish(inst);

            verify(valueOps, never()).set(anyString(), anyString());
            verify(kafka, never()).send(anyString(), anyString(), anyString());

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any());
            verify(kafka, times(1)).send(anyString(), anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void selectByUserId_shouldFallbackToDb_whenRedisMembersThrows() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenThrow(new RuntimeException("redis down"));

        UserTaskInstance db1 = new UserTaskInstance();
        db1.setUserId(66L);
        db1.setTaskId(201L);
        when(mapper.selectByUserId(66L)).thenReturn(List.of(db1));

        List<UserTaskInstance> out = service.selectByUserId(66L);

        assertEquals(1, out.size());
        verify(mapper, times(1)).selectByUserId(66L);
    }

    @Test
    public void selectByUserId_shouldFallbackToDb_whenSetContainsInvalidTaskId() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Set.of("abc"));

        UserTaskInstance db1 = new UserTaskInstance();
        db1.setUserId(67L);
        db1.setTaskId(202L);
        when(mapper.selectByUserId(67L)).thenReturn(List.of(db1));

        List<UserTaskInstance> out = service.selectByUserId(67L);

        assertEquals(1, out.size());
        verify(mapper, times(1)).selectByUserId(67L);
    }

    @Test
    public void acceptTask_shouldPromote_whenExistingStatusNull() {
        UserTaskInstance existing = new UserTaskInstance();
        existing.setUserId(5L);
        existing.setTaskId(17L);
        existing.setStatus(null);

        TaskConfig cfg = new TaskConfig();
        cfg.setId(17L);
        cfg.setStatus(1);
        cfg.setStartTime(new Date(System.currentTimeMillis() - 1000));
        cfg.setEndTime(new Date(System.currentTimeMillis() + 100000));

        when(taskConfigService.getTaskConfig(17L)).thenReturn(cfg);
        when(mapper.selectByUserAndTask(5L, 17L)).thenReturn(existing);
        when(mapper.updateById(any())).thenReturn(1);

        UserTaskInstance out = service.acceptTask(5L, 17L);

        assertNotNull(out);
        assertEquals(UserTaskStatus.ACCEPTED.getCode(), out.getStatus());
        verify(mapper, times(1)).updateById(any());
    }

    @Test
    public void getAcceptedInstance_returnsNull_whenDbStatusNonPositive() {
        when(valueOps.get(anyString())).thenReturn(null);
        UserTaskInstance db = new UserTaskInstance();
        db.setUserId(1L);
        db.setTaskId(3L);
        db.setStatus(0);
        when(mapper.selectByUserAndTask(1L, 3L)).thenReturn(db);

        UserTaskInstance out = service.getAcceptedInstance(1L, 3L);

        assertNull(out);
    }

    @Test
    public void listByConditions_shouldHandleNullStatusAndLegacyTargetField() {
        Page<UserTaskInstance> page = new Page<>(1, 10);

        UserTaskInstance r1 = new UserTaskInstance();
        r1.setTaskId(501L);
        r1.setStatus(null);
        r1.setProgress(0);

        UserTaskInstance r2 = new UserTaskInstance();
        r2.setTaskId(502L);
        r2.setStatus(UserTaskStatus.ACCEPTED.getCode());
        r2.setProgress(3);

        Page<UserTaskInstance> result = new Page<>(1, 10);
        result.setRecords(Arrays.asList(r1, r2));
        when(mapper.selectPage(eq(page), any())).thenReturn(result);

        TaskConfig cfg1 = new TaskConfig();
        cfg1.setId(501L);
        cfg1.setTaskType("ACCUMULATE");
        cfg1.setRuleConfig("{}");

        TaskConfig cfg2 = new TaskConfig();
        cfg2.setId(502L);
        cfg2.setTaskType("ACCUMULATE");
        cfg2.setRuleConfig("{\"target\":2}");

        when(taskConfigService.getTaskConfigsByIds(anySet())).thenReturn(Map.of(501L, cfg1, 502L, cfg2));

        Page<UserTaskInstance> out = service.listByConditions(page, null, null, null);

        assertEquals(UserTaskStatus.ACCEPTED.getCode(), out.getRecords().get(0).getStatus());
        assertEquals(0, out.getRecords().get(0).getProgress());
        assertEquals(UserTaskStatus.COMPLETED.getCode(), out.getRecords().get(1).getStatus());
        assertEquals(100, out.getRecords().get(1).getProgress());
    }

    @Test
    public void selectByUserId_shouldReturnFromCache_whenAllInstancesHit() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Set.of("100", "101"));

        UserTaskInstance i1 = new UserTaskInstance();
        i1.setUserId(88L);
        i1.setTaskId(100L);
        UserTaskInstance i2 = new UserTaskInstance();
        i2.setUserId(88L);
        i2.setTaskId(101L);

        when(valueOps.multiGet(anyList())).thenReturn(List.of(JSON.toJSONString(i1), JSON.toJSONString(i2)));

        List<UserTaskInstance> out = service.selectByUserId(88L);

        assertEquals(2, out.size());
        verify(mapper, never()).selectByUserId(anyLong());
    }

    @Test
    public void selectByUserId_shouldFallbackToDb_whenCachePartialMiss() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Set.of("100", "101"));

        UserTaskInstance i1 = new UserTaskInstance();
        i1.setUserId(77L);
        i1.setTaskId(100L);
        when(valueOps.multiGet(anyList())).thenReturn(Arrays.asList(JSON.toJSONString(i1), null));

        UserTaskInstance db1 = new UserTaskInstance();
        db1.setUserId(77L);
        db1.setTaskId(100L);
        UserTaskInstance db2 = new UserTaskInstance();
        db2.setUserId(77L);
        db2.setTaskId(101L);
        when(mapper.selectByUserId(77L)).thenReturn(List.of(db1, db2));

        List<UserTaskInstance> out = service.selectByUserId(77L);

        assertEquals(2, out.size());
        verify(mapper, times(1)).selectByUserId(77L);
        verify(valueOps, atLeast(2)).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    public void acceptTask_throws_whenNotStarted() {
        TaskConfig cfg = new TaskConfig();
        cfg.setId(41L);
        cfg.setStatus(1);
        cfg.setStartTime(new Date(System.currentTimeMillis() + 60_000));
        cfg.setEndTime(new Date(System.currentTimeMillis() + 120_000));
        when(taskConfigService.getTaskConfig(41L)).thenReturn(cfg);

        assertThrows(BusinessException.class, () -> service.acceptTask(3L, 41L));
    }

    @Test
    public void acceptTask_throws_whenAlreadyEnded() {
        TaskConfig cfg = new TaskConfig();
        cfg.setId(42L);
        cfg.setStatus(1);
        cfg.setStartTime(new Date(System.currentTimeMillis() - 120_000));
        cfg.setEndTime(new Date(System.currentTimeMillis() - 60_000));
        when(taskConfigService.getTaskConfig(42L)).thenReturn(cfg);

        assertThrows(BusinessException.class, () -> service.acceptTask(3L, 42L));
    }

    @Test
    public void acceptTask_shouldReturnExistingAccepted_whenConcurrentInsertFallbackHit() {
        TaskConfig cfg = new TaskConfig();
        cfg.setId(43L);
        cfg.setStatus(1);
        cfg.setStartTime(new Date(System.currentTimeMillis() - 1000));
        cfg.setEndTime(new Date(System.currentTimeMillis() + 100000));
        when(taskConfigService.getTaskConfig(43L)).thenReturn(cfg);
        UserTaskInstance accepted = new UserTaskInstance();
        accepted.setUserId(9L);
        accepted.setTaskId(43L);
        accepted.setStatus(1);
        when(mapper.selectByUserAndTask(9L, 43L)).thenReturn(null).thenReturn(accepted);
        when(mapper.insert(any())).thenThrow(new RuntimeException("dup"));

        UserTaskInstance out = service.acceptTask(9L, 43L);

        assertNotNull(out);
        assertEquals(1, out.getStatus());
    }

    @Test
    public void getAcceptedInstance_shouldFallbackToDb_whenCachedJsonInvalid() {
        when(valueOps.get(anyString())).thenReturn("{bad-json");
        UserTaskInstance db = new UserTaskInstance();
        db.setUserId(1L);
        db.setTaskId(5L);
        db.setStatus(1);
        when(mapper.selectByUserAndTask(1L, 5L)).thenReturn(db);

        UserTaskInstance out = service.getAcceptedInstance(1L, 5L);

        assertNotNull(out);
        assertEquals(1, out.getStatus());
    }

    @Test
    public void listByConditions_shouldSetProgress100_whenTargetNonPositiveAndProgressPositive() {
        Page<UserTaskInstance> page = new Page<>(1, 10);

        UserTaskInstance r = new UserTaskInstance();
        r.setTaskId(601L);
        r.setStatus(UserTaskStatus.ACCEPTED.getCode());
        r.setProgress(2);

        Page<UserTaskInstance> result = new Page<>(1, 10);
        result.setRecords(List.of(r));
        when(mapper.selectPage(eq(page), any())).thenReturn(result);

        TaskConfig cfg = new TaskConfig();
        cfg.setId(601L);
        cfg.setTaskType("ACCUMULATE");
        cfg.setRuleConfig("{}");
        when(taskConfigService.getTaskConfigsByIds(anySet())).thenReturn(Map.of(601L, cfg));

        Page<UserTaskInstance> out = service.listByConditions(page, null, null, null);

        assertEquals(UserTaskStatus.COMPLETED.getCode(), out.getRecords().get(0).getStatus());
        assertEquals(100, out.getRecords().get(0).getProgress());
    }

    @Test
    public void getAcceptedInstance_returnsNull_whenDbStatusIsNull() {
        when(valueOps.get(anyString())).thenReturn(null);
        UserTaskInstance db = new UserTaskInstance();
        db.setUserId(1L);
        db.setTaskId(2L);
        db.setStatus(null);
        when(mapper.selectByUserAndTask(1L, 2L)).thenReturn(db);

        UserTaskInstance out = service.getAcceptedInstance(1L, 2L);

        assertNull(out);
    }

    @Test
    public void privateHelpers_shouldCoverResolveTargetAndStatusProgressBranches() throws Exception {
        Method resolveTarget = UserTaskInstanceServiceImpl.class.getDeclaredMethod("resolveTarget", TaskConfig.class);
        resolveTarget.setAccessible(true);
        Method normalize = UserTaskInstanceServiceImpl.class.getDeclaredMethod("normalizeStatusForDisplay", Integer.class, int.class, int.class);
        normalize.setAccessible(true);
        Method percent = UserTaskInstanceServiceImpl.class.getDeclaredMethod("calcProgressPercent", int.class, int.class, int.class);
        percent.setAccessible(true);

        TaskConfig stair = new TaskConfig();
        stair.setTaskType("STAIR");
        stair.setRuleConfig("{\"stages\":[10,null,30]}");
        assertEquals(30, (Integer) resolveTarget.invoke(service, stair));

        TaskConfig continuousNoDays = new TaskConfig();
        continuousNoDays.setTaskType("CONTINUOUS");
        continuousNoDays.setRuleConfig("{}");
        assertEquals(0, (Integer) resolveTarget.invoke(service, continuousNoDays));

        TaskConfig legacyTarget = new TaskConfig();
        legacyTarget.setTaskType("ACCUMULATE");
        legacyTarget.setRuleConfig("{\"target\":7}");
        assertEquals(7, (Integer) resolveTarget.invoke(service, legacyTarget));

        TaskConfig badJson = new TaskConfig();
        badJson.setTaskType("ACCUMULATE");
        badJson.setRuleConfig("{bad");
        assertEquals(0, (Integer) resolveTarget.invoke(service, badJson));

        assertEquals(UserTaskStatus.CANCELLED.getCode(), (Integer) normalize.invoke(service, 4, 10, 100));
        assertEquals(UserTaskStatus.COMPLETED.getCode(), (Integer) normalize.invoke(service, 1, 10, 10));
        assertEquals(UserTaskStatus.IN_PROGRESS.getCode(), (Integer) normalize.invoke(service, 1, 3, 10));
        assertEquals(UserTaskStatus.ACCEPTED.getCode(), (Integer) normalize.invoke(service, null, 0, 10));

        assertEquals(100, (Integer) percent.invoke(service, 3, 0, UserTaskStatus.ACCEPTED.getCode()));
        assertEquals(0, (Integer) percent.invoke(service, 0, 0, UserTaskStatus.ACCEPTED.getCode()));
        assertEquals(100, (Integer) percent.invoke(service, 999, 10, UserTaskStatus.IN_PROGRESS.getCode()));
    }

    @Test
    public void privateHelper_fillDisplayNames_shouldCoverNullSkipAndFillBranches() throws Exception {
        Method fill = UserTaskInstanceServiceImpl.class.getDeclaredMethod("fillDisplayNames", UserTaskInstance.class, Long.class, Long.class);
        fill.setAccessible(true);

        // null instance branch
        fill.invoke(service, null, 1L, 2L);

        UserTaskInstance instance = new UserTaskInstance();
        instance.setUserName("");
        instance.setTaskName("");

        com.whu.graduation.taskincentive.dao.mapper.UserMapper userMapper = mock(com.whu.graduation.taskincentive.dao.mapper.UserMapper.class);
        java.lang.reflect.Field fUser = UserTaskInstanceServiceImpl.class.getDeclaredField("userMapper");
        fUser.setAccessible(true);
        fUser.set(service, userMapper);

        com.whu.graduation.taskincentive.dao.entity.User user = new com.whu.graduation.taskincentive.dao.entity.User();
        user.setUsername("u1");
        when(userMapper.selectById(1L)).thenReturn(user);

        TaskConfig cfg = new TaskConfig();
        cfg.setTaskName("t1");
        when(taskConfigService.getTaskConfig(2L)).thenReturn(cfg);

        fill.invoke(service, instance, 1L, 2L);

        assertEquals("u1", instance.getUserName());
        assertEquals("t1", instance.getTaskName());

        // already set -> no query branches
        reset(userMapper, taskConfigService);
        instance.setUserName("kept");
        instance.setTaskName("kept-task");
        fill.invoke(service, instance, 1L, 2L);
        verify(userMapper, never()).selectById(any());
        verify(taskConfigService, never()).getTaskConfig(anyLong());
    }

    @Test
    public void selectByUserId_shouldFallbackToDb_whenMultiGetReturnsNull() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Set.of("301", "302"));
        when(valueOps.multiGet(anyList())).thenReturn(null);

        UserTaskInstance db1 = new UserTaskInstance();
        db1.setUserId(90L);
        db1.setTaskId(301L);
        UserTaskInstance db2 = new UserTaskInstance();
        db2.setUserId(90L);
        db2.setTaskId(302L);
        when(mapper.selectByUserId(90L)).thenReturn(List.of(db1, db2));

        List<UserTaskInstance> out = service.selectByUserId(90L);

        assertEquals(2, out.size());
        verify(mapper, times(1)).selectByUserId(90L);
    }

    @Test
    public void selectByUserId_shouldFallbackToDb_whenAcceptedSetNullOrEmpty() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        UserTaskInstance db = new UserTaskInstance();
        db.setUserId(91L);
        db.setTaskId(401L);
        when(mapper.selectByUserId(91L)).thenReturn(List.of(db));

        when(setOps.members(anyString())).thenReturn(null);
        List<UserTaskInstance> out1 = service.selectByUserId(91L);
        assertEquals(1, out1.size());

        when(setOps.members(anyString())).thenReturn(Collections.emptySet());
        List<UserTaskInstance> out2 = service.selectByUserId(91L);
        assertEquals(1, out2.size());
    }

    @Test
    public void selectByUserId_shouldIgnoreNullParsedCacheEntries_andFallbackToDb() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Set.of("501"));
        when(valueOps.multiGet(anyList())).thenReturn(List.of("null"));

        UserTaskInstance db = new UserTaskInstance();
        db.setUserId(92L);
        db.setTaskId(501L);
        when(mapper.selectByUserId(92L)).thenReturn(List.of(db));

        List<UserTaskInstance> out = service.selectByUserId(92L);

        assertEquals(1, out.size());
        verify(mapper, times(1)).selectByUserId(92L);
    }

    @Test
    public void selectByUserId_shouldReturnDbDirectly_whenDbEmptyAfterFallback() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Set.of("601"));
        when(valueOps.multiGet(anyList())).thenReturn(Arrays.asList((String) null));
        when(mapper.selectByUserId(93L)).thenReturn(Collections.emptyList());

        List<UserTaskInstance> out = service.selectByUserId(93L);

        assertTrue(out.isEmpty());
    }

    @Test
    public void update_shouldSkipCacheWrite_whenStatusNull() {
        UserTaskInstance inst = new UserTaskInstance();
        inst.setId(104L);
        inst.setUserId(10L);
        inst.setTaskId(23L);
        inst.setStatus(null);
        when(mapper.updateById(any())).thenReturn(1);

        boolean updated = service.update(inst);

        assertTrue(updated);
        verify(valueOps, never()).set(anyString(), anyString());
    }

    @Test
    public void getOrCreateWithCache_shouldReturnNull_whenConcurrentCreateFallbackStillNull() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(mapper.selectByUserAndTask(94L, 701L)).thenReturn(null).thenReturn(null);
        when(mapper.insert(any())).thenThrow(new RuntimeException("dup"));

        UserTaskInstance out = service.getOrCreateWithCache(94L, 701L);

        assertNull(out);
    }

    @Test
    public void updateAndPublish_shouldSkipRedisWrite_whenPayloadSerializationFails_noTx() throws Exception {
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka = mock(org.springframework.kafka.core.KafkaTemplate.class);
        java.lang.reflect.Field fKafka = UserTaskInstanceServiceImpl.class.getDeclaredField("kafkaTemplate");
        fKafka.setAccessible(true);
        fKafka.set(service, kafka);

        UserTaskInstance bad = new UserTaskInstance() {
            @Override
            public Integer getVersion() {
                throw new RuntimeException("boom");
            }
        };
        bad.setUserId(100L);
        bad.setTaskId(999L);

        service.updateAndPublish(bad);

        verify(valueOps, never()).set(anyString(), anyString());
    }

    @Test
    public void updateAndPublish_shouldSkipRedisWrite_whenPayloadSerializationFails_inTx() throws Exception {
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka = mock(org.springframework.kafka.core.KafkaTemplate.class);
        java.lang.reflect.Field fKafka = UserTaskInstanceServiceImpl.class.getDeclaredField("kafkaTemplate");
        fKafka.setAccessible(true);
        fKafka.set(service, kafka);

        UserTaskInstance bad = new UserTaskInstance() {
            @Override
            public Integer getVersion() {
                throw new RuntimeException("boom");
            }
        };
        bad.setUserId(101L);
        bad.setTaskId(1000L);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateAndPublish(bad);
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(valueOps, never()).set(anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void updateAndPublish_shouldWriteRedisAndKafkaImmediately_whenNoTransaction() throws Exception {
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka = mock(org.springframework.kafka.core.KafkaTemplate.class);
        java.lang.reflect.Field fKafka = UserTaskInstanceServiceImpl.class.getDeclaredField("kafkaTemplate");
        fKafka.setAccessible(true);
        fKafka.set(service, kafka);

        UserTaskInstance inst = new UserTaskInstance();
        inst.setId(1201L);
        inst.setUserId(55L);
        inst.setTaskId(66L);
        inst.setStatus(1);

        service.updateAndPublish(inst);

        verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any());
        verify(kafka, times(1)).send(anyString(), eq("55"), anyString());
    }

    @Test
    public void acceptTask_shouldThrow_whenConcurrentFallbackReturnsNotAccepted() {
        TaskConfig cfg = new TaskConfig();
        cfg.setId(3001L);
        cfg.setStatus(1);
        cfg.setStartTime(new Date(System.currentTimeMillis() - 1000));
        cfg.setEndTime(new Date(System.currentTimeMillis() + 100000));
        when(taskConfigService.getTaskConfig(3001L)).thenReturn(cfg);

        UserTaskInstance fallback = new UserTaskInstance();
        fallback.setUserId(7L);
        fallback.setTaskId(3001L);
        fallback.setStatus(0);
        when(mapper.selectByUserAndTask(7L, 3001L)).thenReturn(null).thenReturn(fallback);
        when(mapper.insert(any())).thenThrow(new RuntimeException("dup"));

        assertThrows(BusinessException.class, () -> service.acceptTask(7L, 3001L));
    }

    @Test
    public void getAcceptedInstance_returnsNull_whenCachedJsonIsNullLiteral() {
        when(valueOps.get(anyString())).thenReturn("null");

        UserTaskInstance out = service.getAcceptedInstance(11L, 22L);

        assertNull(out);
        verify(mapper, never()).selectByUserAndTask(anyLong(), anyLong());
    }

    @Test
    public void getOrCreate_shouldReturnExisting_whenFoundInDb() {
        UserTaskInstance existing = new UserTaskInstance();
        existing.setUserId(21L);
        existing.setTaskId(210L);
        existing.setStatus(1);
        when(mapper.selectByUserAndTask(21L, 210L)).thenReturn(existing);

        UserTaskInstance out = service.getOrCreate(21L, 210L);

        assertSame(existing, out);
        verify(mapper, never()).insert(any());
    }

    @Test
    public void getOrCreate_shouldInsertAndReturnNewInstance_whenNotFound() {
        when(mapper.selectByUserAndTask(22L, 220L)).thenReturn(null);
        when(mapper.insert(any())).thenReturn(1);

        UserTaskInstance out = service.getOrCreate(22L, 220L);

        assertNotNull(out);
        assertEquals(22L, out.getUserId());
        assertEquals(220L, out.getTaskId());
        assertEquals(0, out.getStatus());
        verify(mapper, times(1)).insert(any());
    }

    @Test
    public void selectByUserIdPage_shouldCallMapper_whenStatusNullOrPresent() {
        Page<UserTaskInstance> page = new Page<>(1, 10);
        when(mapper.selectPage(eq(page), any())).thenReturn(page);

        Page<UserTaskInstance> out1 = service.selectByUserIdPage(page, 31L, null);
        Page<UserTaskInstance> out2 = service.selectByUserIdPage(page, 31L, 1);

        assertSame(page, out1);
        assertSame(page, out2);
        verify(mapper, times(2)).selectPage(eq(page), any());
    }

    @Test
    public void delegateMethods_shouldCoverSaveGetListAndStatusQuery() {
        UserTaskInstance one = new UserTaskInstance();
        one.setId(5001L);

        when(mapper.insert(any())).thenReturn(1);
        assertTrue(service.save(new UserTaskInstance()));

        when(mapper.selectById(5001L)).thenReturn(one);
        assertEquals(5001L, service.getById(5001L).getId());

        when(mapper.selectList(any())).thenReturn(List.of(one));
        assertEquals(1, service.listAll().size());

        when(mapper.selectByUserIdAndStatus(9L, 1)).thenReturn(List.of(one));
        assertEquals(1, service.selectByUserIdAndStatus(9L, 1).size());
    }

    @Test
    public void updateWithVersion_andListByConditions_shouldCoverRemainingBranches() {
        UserTaskInstance one = new UserTaskInstance();
        one.setId(6001L);
        one.setTaskId(null);
        one.setProgress(5);
        one.setStatus(1);

        when(mapper.updateWithVersion(any(UserTaskInstance.class))).thenReturn(1);
        assertEquals(1, service.updateWithVersion(one));

        Page<UserTaskInstance> page = new Page<>(1, 10);
        Page<UserTaskInstance> db = new Page<>(1, 10);
        db.setRecords(Arrays.asList(null, one));
        when(mapper.selectPage(eq(page), any())).thenReturn(db);
        when(taskConfigService.getTaskConfigsByIds(anySet())).thenReturn(Collections.emptyMap());

        Page<UserTaskInstance> out = service.listByConditions(page, 1L, null, null);
        assertEquals(2, out.getRecords().size());
        assertEquals(UserTaskStatus.COMPLETED.getCode(), out.getRecords().get(1).getStatus());
        assertEquals(100, out.getRecords().get(1).getProgress());
    }

    @Test
    public void acceptTask_shouldWriteRedisAfterCommit_whenPromoteExistingInTransaction() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        TaskConfig cfg = new TaskConfig();
        cfg.setId(8005L);
        cfg.setStatus(1);
        cfg.setStartTime(new Date(System.currentTimeMillis() - 1000));
        cfg.setEndTime(new Date(System.currentTimeMillis() + 100000));
        when(taskConfigService.getTaskConfig(8005L)).thenReturn(cfg);

        UserTaskInstance existing = new UserTaskInstance();
        existing.setUserId(12L);
        existing.setTaskId(8005L);
        existing.setStatus(0);
        when(mapper.selectByUserAndTask(12L, 8005L)).thenReturn(existing);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.acceptTask(12L, 8005L);
            verify(valueOps, never()).set(anyString(), anyString());
            verify(setOps, never()).add(anyString(), anyString());

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any());
            verify(setOps, times(1)).add(anyString(), eq("8005"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void acceptTask_shouldWriteRedisAfterCommit_whenCreateNewInTransaction() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        TaskConfig cfg = new TaskConfig();
        cfg.setId(8006L);
        cfg.setStatus(1);
        cfg.setStartTime(new Date(System.currentTimeMillis() - 1000));
        cfg.setEndTime(new Date(System.currentTimeMillis() + 100000));
        when(taskConfigService.getTaskConfig(8006L)).thenReturn(cfg);
        when(mapper.selectByUserAndTask(13L, 8006L)).thenReturn(null);
        when(mapper.insert(any())).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.acceptTask(13L, 8006L);
            verify(valueOps, never()).set(anyString(), anyString());
            verify(setOps, never()).add(anyString(), anyString());

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any());
            verify(setOps, times(1)).add(anyString(), eq("8006"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void deleteById_shouldReturnFalse_whenRemoveFailed() {
        UserTaskInstance existing = new UserTaskInstance();
        existing.setId(9001L);
        existing.setUserId(15L);
        existing.setTaskId(901L);
        existing.setStatus(1);
        when(mapper.selectById(9001L)).thenReturn(existing);
        when(mapper.deleteById(9001L)).thenReturn(0);

        boolean out = service.deleteById(9001L);

        assertFalse(out);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    public void deleteById_shouldEvictRedisImmediately_whenAcceptedAndNoTransaction() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        UserTaskInstance existing = new UserTaskInstance();
        existing.setId(9002L);
        existing.setUserId(16L);
        existing.setTaskId(902L);
        existing.setStatus(1);
        when(mapper.selectById(9002L)).thenReturn(existing);
        when(mapper.deleteById(9002L)).thenReturn(1);

        boolean out = service.deleteById(9002L);

        assertTrue(out);
        verify(setOps, times(1)).remove(anyString(), eq("902"));
        verify(redisTemplate, times(1)).delete(anyString());
    }

    @Test
    public void deleteById_shouldEvictRedisAfterCommit_whenTransactionActive() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        UserTaskInstance existing = new UserTaskInstance();
        existing.setId(9003L);
        existing.setUserId(17L);
        existing.setTaskId(903L);
        existing.setStatus(1);
        when(mapper.selectById(9003L)).thenReturn(existing);
        when(mapper.deleteById(9003L)).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            boolean out = service.deleteById(9003L);
            assertTrue(out);

            verify(setOps, never()).remove(anyString(), anyString());
            verify(redisTemplate, never()).delete(anyString());

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(setOps, times(1)).remove(anyString(), eq("903"));
            verify(redisTemplate, times(1)).delete(anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void updateAndPublish_shouldSwallowKafkaSendException_whenNoTransaction() throws Exception {
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka = mock(org.springframework.kafka.core.KafkaTemplate.class);
        java.lang.reflect.Field fKafka = UserTaskInstanceServiceImpl.class.getDeclaredField("kafkaTemplate");
        fKafka.setAccessible(true);
        fKafka.set(service, kafka);

        UserTaskInstance inst = new UserTaskInstance();
        inst.setId(9301L);
        inst.setUserId(88L);
        inst.setTaskId(9901L);
        inst.setStatus(1);

        doThrow(new RuntimeException("kafka down")).when(kafka).send(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> service.updateAndPublish(inst));
        verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any());
        verify(kafka, times(1)).send(anyString(), eq("88"), anyString());
    }

    @Test
    public void deleteById_shouldReturnTrueAndSkipCacheEvict_whenInstanceMissing() {
        when(mapper.selectById(9401L)).thenReturn(null);
        when(mapper.deleteById(9401L)).thenReturn(1);

        boolean out = service.deleteById(9401L);

        assertTrue(out);
        verify(redisTemplate, never()).delete(anyString());
        verify(redisTemplate, never()).opsForSet();
    }

    @Test
    public void deleteById_shouldReturnTrueAndSkipCacheEvict_whenStatusNonPositive() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        UserTaskInstance existing = new UserTaskInstance();
        existing.setId(9402L);
        existing.setUserId(31L);
        existing.setTaskId(3201L);
        existing.setStatus(0);
        when(mapper.selectById(9402L)).thenReturn(existing);
        when(mapper.deleteById(9402L)).thenReturn(1);

        boolean out = service.deleteById(9402L);

        assertTrue(out);
        verify(setOps, never()).remove(anyString(), anyString());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    public void acceptTask_shouldReturnExisting_whenStatusGreaterThanAccepted() {
        UserTaskInstance existing = new UserTaskInstance();
        existing.setUserId(41L);
        existing.setTaskId(4101L);
        existing.setStatus(2);

        TaskConfig cfg = new TaskConfig();
        cfg.setId(4101L);
        cfg.setStatus(1);
        cfg.setStartTime(new Date(System.currentTimeMillis() - 1000));
        cfg.setEndTime(new Date(System.currentTimeMillis() + 100000));

        when(taskConfigService.getTaskConfig(4101L)).thenReturn(cfg);
        when(mapper.selectByUserAndTask(41L, 4101L)).thenReturn(existing);

        UserTaskInstance out = service.acceptTask(41L, 4101L);

        assertSame(existing, out);
        verify(mapper, never()).updateById(any());
        verify(mapper, never()).insert(any());
    }

    @Test
    public void update_shouldKeepSuccess_whenRedisWriteThrows_noTransaction() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        UserTaskInstance inst = new UserTaskInstance();
        inst.setId(9403L);
        inst.setUserId(32L);
        inst.setTaskId(3202L);
        inst.setStatus(1);
        when(mapper.updateById(any())).thenReturn(1);
        doThrow(new RuntimeException("sadd fail")).when(setOps).add(anyString(), anyString());
        doThrow(new RuntimeException("set fail")).when(valueOps).set(anyString(), anyString());

        boolean out = assertDoesNotThrow(() -> service.update(inst));
        assertTrue(out);
    }

    @Test
    public void warmupHotUserTaskInstances_shouldWarmCachesWithinLimits() {
        when(mapper.selectHotUserIds(2)).thenReturn(List.of(1001L, 1002L));

        UserTaskInstance i1 = new UserTaskInstance();
        i1.setUserId(1001L);
        i1.setTaskId(501L);
        i1.setStatus(1);
        UserTaskInstance i2 = new UserTaskInstance();
        i2.setUserId(1001L);
        i2.setTaskId(502L);
        i2.setStatus(1);
        UserTaskInstance i3 = new UserTaskInstance();
        i3.setUserId(1002L);
        i3.setTaskId(601L);
        i3.setStatus(1);

        when(mapper.selectAcceptedByUserIdsLimited(anyList(), eq(2), eq(4))).thenReturn(List.of(i1, i2, i3));

        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        UserTaskInstanceService.HotUserWarmupStats stats =
                service.warmupHotUserTaskInstances(2, 2, 10, 15);

        assertEquals(2, stats.getUserCount());
        assertEquals(3, stats.getInstanceCount());
        assertFalse(stats.isTruncated());
        verify(valueOps, times(3)).set(startsWith("userTask:"), anyString(), eq(15L), eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(redisTemplate, atLeastOnce()).opsForSet();
        verify(redisTemplate, atLeastOnce()).expire(startsWith("user:accepted:"), eq(15L), eq(java.util.concurrent.TimeUnit.MINUTES));
    }

    @Test
    public void warmupHotUserTaskInstances_shouldTruncateWhenTotalLimitReached() {
        when(mapper.selectHotUserIds(2)).thenReturn(List.of(2001L, 2002L));

        UserTaskInstance i1 = new UserTaskInstance();
        i1.setUserId(2001L);
        i1.setTaskId(701L);
        i1.setStatus(1);
        UserTaskInstance i2 = new UserTaskInstance();
        i2.setUserId(2001L);
        i2.setTaskId(702L);
        i2.setStatus(1);
        UserTaskInstance i3 = new UserTaskInstance();
        i3.setUserId(2002L);
        i3.setTaskId(801L);
        i3.setStatus(1);

        when(mapper.selectAcceptedByUserIdsLimited(anyList(), eq(5), eq(2))).thenReturn(List.of(i1, i2, i3));

        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        UserTaskInstanceService.HotUserWarmupStats stats =
                service.warmupHotUserTaskInstances(2, 5, 2, 10);

        assertEquals(1, stats.getUserCount());
        assertEquals(2, stats.getInstanceCount());
        assertTrue(stats.isTruncated());
        verify(valueOps, times(2)).set(startsWith("userTask:"), anyString(), eq(10L), eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(redisTemplate, atLeastOnce()).opsForSet();
        verify(redisTemplate, atLeastOnce()).expire(startsWith("user:accepted:"), eq(10L), eq(java.util.concurrent.TimeUnit.MINUTES));
    }

    @Test
    public void warmupHotUserTaskInstances_shouldFallbackToPerUserQuery_whenBatchQueryFails() {
        when(mapper.selectHotUserIds(2)).thenReturn(List.of(3001L, 3002L));
        when(mapper.selectAcceptedByUserIdsLimited(anyList(), eq(2), eq(4))).thenThrow(new RuntimeException("sql not supported"));

        UserTaskInstance i1 = new UserTaskInstance();
        i1.setUserId(3001L);
        i1.setTaskId(901L);
        i1.setStatus(1);
        UserTaskInstance i2 = new UserTaskInstance();
        i2.setUserId(3002L);
        i2.setTaskId(902L);
        i2.setStatus(1);
        when(mapper.selectAcceptedByUserIdLimited(3001L, 2)).thenReturn(List.of(i1));
        when(mapper.selectAcceptedByUserIdLimited(3002L, 2)).thenReturn(List.of(i2));

        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        UserTaskInstanceService.HotUserWarmupStats stats =
                service.warmupHotUserTaskInstances(2, 2, 10, 5);

        assertEquals(2, stats.getUserCount());
        assertEquals(2, stats.getInstanceCount());
        assertFalse(stats.isTruncated());
        verify(mapper, times(1)).selectAcceptedByUserIdLimited(3001L, 2);
        verify(mapper, times(1)).selectAcceptedByUserIdLimited(3002L, 2);
    }
}
