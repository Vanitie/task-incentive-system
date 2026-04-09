package com.whu.graduation.taskincentive.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.mapper.TaskConfigMapper;
import com.whu.graduation.taskincentive.service.impl.TaskConfigServiceImpl;
import com.whu.graduation.taskincentive.service.TaskStockService;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.github.benmanes.caffeine.cache.Cache;

public class TaskConfigServiceImplTest {

    private TaskConfigServiceImpl service;
    private RedisTemplate<String, String> redisTemplate;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOps;
    private TaskStockService taskStockService;
    private TaskConfigMapper mapper;

    @BeforeEach
    public void setup() throws Exception {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        service = new TaskConfigServiceImpl(stringRedisTemplate);

        redisTemplate = mock(RedisTemplate.class);
        //noinspection unchecked
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        taskStockService = mock(TaskStockService.class);

        mapper = mock(TaskConfigMapper.class);

        // inject mocks via reflection
        java.lang.reflect.Field fRedis = TaskConfigServiceImpl.class.getDeclaredField("redisTemplate");
        fRedis.setAccessible(true);
        fRedis.set(service, redisTemplate);

        java.lang.reflect.Field fStock = TaskConfigServiceImpl.class.getDeclaredField("taskStockService");
        fStock.setAccessible(true);
        fStock.set(service, taskStockService);

        // baseMapper is defined in superclass ServiceImpl
        java.lang.reflect.Field fBase = service.getClass().getSuperclass().getDeclaredField("baseMapper");
        fBase.setAccessible(true);
        fBase.set(service, mapper);
    }

    @Test
    public void getTaskConfig_readsFromRedis_andCaches() {
        Long id = 1001L;
        TaskConfig cfg = new TaskConfig();
        cfg.setId(id);
        cfg.setTaskName("t1");
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(valueOps.get(key)).thenReturn(JSON.toJSONString(cfg));

        TaskConfig res = service.getTaskConfig(id);
        assertNotNull(res);
        assertEquals(id, res.getId());
        verify(valueOps, times(1)).get(key);
        // should not call DB
        verify(mapper, never()).selectById(any());

        // second call should hit local cache -> valueOps.get not called again
        reset(valueOps);
        TaskConfig res2 = service.getTaskConfig(id);
        assertNotNull(res2);
    }

    @Test
    public void getTaskConfigsByIds_usesLocalRedisAndDb() {
        Long id1 = 1L, id2 = 2L;
        Set<Long> ids = new LinkedHashSet<>(); // preserve order
        ids.add(id1);
        ids.add(id2);

        TaskConfig cfg1 = new TaskConfig(); cfg1.setId(id1); cfg1.setTaskName("a");
        TaskConfig cfg2 = new TaskConfig(); cfg2.setId(id2); cfg2.setTaskName("b");

        List<String> multi = Arrays.asList(JSON.toJSONString(cfg1), null);
        List<String> keys = Arrays.asList(CacheKeys.TASK_CONFIG_PREFIX + id1, CacheKeys.TASK_CONFIG_PREFIX + id2);
        when(redisTemplate.opsForValue().multiGet(keys)).thenReturn(multi);

        when(mapper.selectBatchIds(anySet())).thenReturn(Arrays.asList(cfg2));

        Map<Long, TaskConfig> result = service.getTaskConfigsByIds(ids);
        assertEquals(2, result.size());
        assertTrue(result.containsKey(id1));
        assertTrue(result.containsKey(id2));

        verify(redisTemplate.opsForValue(), times(1)).multiGet(keys);
        verify(mapper, times(1)).selectBatchIds(any());
    }

    @Test
    public void directMethods_shouldReadFromDbOnly() {
        TaskConfig tc = new TaskConfig();
        tc.setId(701L);
        tc.setTaskName("direct");
        when(mapper.selectList(any())).thenReturn(List.of(tc));
        when(mapper.selectBatchIds(Set.of(701L))).thenReturn(List.of(tc));

        Set<String> ids = service.getTaskIdsByEventTypeDirect("USER_LEARN");
        Map<Long, TaskConfig> cfgMap = service.getTaskConfigsByIdsDirect(Set.of(701L));

        assertEquals(Set.of("701"), ids);
        assertEquals(1, cfgMap.size());
        assertTrue(cfgMap.containsKey(701L));
        verify(mapper, times(1)).selectList(any());
        verify(mapper, times(1)).selectBatchIds(Set.of(701L));
        verify(valueOps, never()).multiGet(anyList());
    }

    @Test
    public void getTaskIdsByEventTypeDirect_shouldReturnEmpty_whenEventTypeBlank() {
        Set<String> ids = service.getTaskIdsByEventTypeDirect("");
        assertNotNull(ids);
        assertTrue(ids.isEmpty());
        verify(mapper, never()).selectList(any());
    }

    @Test
    public void save_limited_createsStock_and_writesRedis() {
        TaskConfig tc = new TaskConfig();
        tc.setStockType("LIMITED");
        tc.setTotalStock(10);
        // spy mapper insert to return 1
        when(mapper.insert(any())).thenAnswer(invocation -> {
            TaskConfig arg = invocation.getArgument(0);
            // simulate id generated (save() sets id via IdWorker internally), but our method will set id before calling insert
            if (arg.getId() == null) arg.setId(9999L);
            return 1;
        });

        // ensure selectById returns same object when getTaskConfig later
        when(mapper.selectById(anyLong())).thenReturn(tc);

        boolean ok = service.save(tc);
        assertTrue(ok);

        // taskStockService.save should be called (no transaction active so immediate save)
        verify(taskStockService, times(1)).save(any());

        // verify redis write occurred via valueOps.set(key, json, 60, TimeUnit.SECONDS)
        verify(valueOps, times(1)).set(anyString(), anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    public void refreshTaskConfig_fallbacksToDb_and_writesRedis() {
        Long id = 200L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(valueOps.get(key)).thenReturn(null);
        TaskConfig cfg = new TaskConfig(); cfg.setId(id); cfg.setTaskName("fromdb");
        when(mapper.selectById(id)).thenReturn(cfg);

        service.refreshTaskConfig(id);

        // should write to redis via valueOps.set
        verify(valueOps, times(1)).set(eq(key), anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    public void getTaskIdsByEventType_readsDb_whenRedisEmpty_and_backfillsRedis() {
        String evt = "EVENT_X";
        String key = CacheKeys.EVENT_TASKS_PREFIX + evt;

        // redis returns null
        //noinspection unchecked
        when(redisTemplate.opsForSet()).thenReturn(mock(org.springframework.data.redis.core.SetOperations.class));
        when(redisTemplate.opsForSet().members(key)).thenReturn(null);

        TaskConfig tc = new TaskConfig(); tc.setId(33L);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(tc));

        Set<String> ids = service.getTaskIdsByEventType(evt);
        assertNotNull(ids);
        assertTrue(ids.contains("33"));
    }

    @Test
    public void getTaskIdsByEventType_shouldReturnRedisMembers_whenPresent() {
        String evt = "EVT_DIRECT";
        String key = CacheKeys.EVENT_TASKS_PREFIX + evt;
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.SetOperations<String, String> setOps = mock(org.springframework.data.redis.core.SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(key)).thenReturn(Set.of("101", "102"));

        Set<String> out = service.getTaskIdsByEventType(evt);

        assertEquals(2, out.size());
        verify(mapper, never()).selectList(any());
    }

    @Test
    public void getTaskIdsByEventType_shouldReturnEmpty_whenRedisFailsAndDbEmpty() {
        String evt = "EVT_EMPTY";
        String key = CacheKeys.EVENT_TASKS_PREFIX + evt;
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.SetOperations<String, String> setOps = mock(org.springframework.data.redis.core.SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(key)).thenThrow(new RuntimeException("redis down"));
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());

        Set<String> out = service.getTaskIdsByEventType(evt);

        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    public void getTaskConfig_shouldFallbackToDb_whenRedisJsonInvalid() {
        Long id = 9701L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(valueOps.get(key)).thenReturn("{bad-json");
        TaskConfig db = new TaskConfig();
        db.setId(id);
        db.setTaskName("db");
        when(mapper.selectById(id)).thenReturn(db);

        TaskConfig out = service.getTaskConfig(id);

        assertNotNull(out);
        assertEquals(id, out.getId());
        verify(valueOps, times(1)).set(eq(key), anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    public void refreshTaskConfig_shouldFallbackToDb_whenRedisReadThrows() {
        Long id = 9702L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(valueOps.get(key)).thenThrow(new RuntimeException("redis read err"));
        TaskConfig db = new TaskConfig();
        db.setId(id);
        when(mapper.selectById(id)).thenReturn(db);

        service.refreshTaskConfig(id);

        verify(valueOps, times(1)).set(eq(key), anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    public void writeAndEvictCacheAfterCommit_shouldRunCallbacks_whenSynchronizationActive() throws Exception {
        Long id = 9703L;
        TaskConfig tc = new TaskConfig();
        tc.setId(id);
        tc.setTaskName("tc");

        TransactionSynchronizationManager.initSynchronization();
        try {
            java.lang.reflect.Method write = TaskConfigServiceImpl.class.getDeclaredMethod("writeCacheAfterCommit", TaskConfig.class);
            write.setAccessible(true);
            write.invoke(service, tc);

            java.lang.reflect.Method evict = TaskConfigServiceImpl.class.getDeclaredMethod("evictCacheAfterCommit", Long.class);
            evict.setAccessible(true);
            evict.invoke(service, id);

            for (org.springframework.transaction.support.TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(valueOps, atLeastOnce()).set(anyString(), anyString(), eq(60L), eq(TimeUnit.SECONDS));
            verify(redisTemplate, atLeastOnce()).delete(anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void createTaskStock_shouldCoverStairAndNonStairBranches() throws Exception {
        java.lang.reflect.Method m = TaskConfigServiceImpl.class.getDeclaredMethod("createTaskStock", TaskConfig.class);
        m.setAccessible(true);

        TaskConfig stair = new TaskConfig();
        stair.setId(9801L);
        stair.setTaskType("STAIR");
        stair.setStockType("LIMITED");
        stair.setTotalStock(8);
        stair.setRuleConfig("{\"stages\":[1,2]}");

        TaskConfig normalLimited = new TaskConfig();
        normalLimited.setId(9802L);
        normalLimited.setTaskType("ACCUMULATE");
        normalLimited.setStockType("LIMITED");
        normalLimited.setTotalStock(5);
        normalLimited.setRuleConfig("{}");

        m.invoke(service, stair);
        m.invoke(service, normalLimited);

        verify(taskStockService, times(1)).deleteById(9801L);
        verify(taskStockService, times(3)).save(any());
    }

    @Test
    public void getTaskIdsByEventType_shouldFallbackToDb_whenRedisReturnsEmptySet() {
        String evt = "EVT_EMPTY_SET";
        String key = CacheKeys.EVENT_TASKS_PREFIX + evt;
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.SetOperations<String, String> setOps = mock(org.springframework.data.redis.core.SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(key)).thenReturn(Collections.emptySet());

        TaskConfig tc = new TaskConfig();
        tc.setId(44L);
        when(mapper.selectList(any())).thenReturn(List.of(tc));

        Set<String> out = service.getTaskIdsByEventType(evt);

        assertEquals(Set.of("44"), out);
        verify(mapper, times(1)).selectList(any());
    }

    @Test
    public void update_shouldSetZeroStock_whenTotalStockNull() {
        TaskConfig tc = new TaskConfig();
        tc.setId(9901L);
        tc.setStockType("LIMITED");
        tc.setTotalStock(null);
        tc.setRuleConfig("{}");
        when(mapper.updateById(any())).thenReturn(1);

        boolean ok = service.update(tc);

        assertTrue(ok);
        ArgumentCaptor<com.whu.graduation.taskincentive.dao.entity.TaskStock> captor = ArgumentCaptor.forClass(com.whu.graduation.taskincentive.dao.entity.TaskStock.class);
        verify(taskStockService).update(captor.capture());
        assertEquals(0, captor.getValue().getAvailableStock());
    }

    @Test
    public void getTaskConfigsByIds_shouldFallbackToDb_whenMultiGetReturnsShortList() {
        Set<Long> ids = new LinkedHashSet<>(Arrays.asList(9101L, 9102L));
        List<String> keys = Arrays.asList(CacheKeys.TASK_CONFIG_PREFIX + 9101L, CacheKeys.TASK_CONFIG_PREFIX + 9102L);
        when(redisTemplate.opsForValue().multiGet(keys)).thenReturn(List.of(JSON.toJSONString(new TaskConfig() {{ setId(9101L); }})));

        TaskConfig db = new TaskConfig();
        db.setId(9102L);
        when(mapper.selectBatchIds(anySet())).thenReturn(List.of(db));

        Map<Long, TaskConfig> out = service.getTaskConfigsByIds(ids);

        assertEquals(2, out.size());
        assertTrue(out.containsKey(9101L));
        assertTrue(out.containsKey(9102L));
    }

    @Test
    public void createTaskStock_shouldSkipDelete_whenStairStagesEmpty() throws Exception {
        java.lang.reflect.Method m = TaskConfigServiceImpl.class.getDeclaredMethod("createTaskStock", TaskConfig.class);
        m.setAccessible(true);

        TaskConfig stairEmpty = new TaskConfig();
        stairEmpty.setId(9902L);
        stairEmpty.setTaskType("STAIR");
        stairEmpty.setStockType("LIMITED");
        stairEmpty.setTotalStock(3);
        stairEmpty.setRuleConfig("{\"stages\":[]}");

        m.invoke(service, stairEmpty);

        verify(taskStockService, never()).deleteById(9902L);
    }

    @Test
    public void save_shouldNormalizeBlankRuleConfig() {
        TaskConfig tc = new TaskConfig();
        tc.setStockType("UNLIMITED");
        tc.setRuleConfig("   ");
        when(mapper.insert(any())).thenReturn(1);

        boolean ok = service.save(tc);

        assertTrue(ok);
        assertEquals("{}", tc.getRuleConfig());
    }

    @Test
    public void update_shouldNormalizeNullRuleConfig() {
        TaskConfig tc = new TaskConfig();
        tc.setId(9903L);
        tc.setStockType("UNLIMITED");
        tc.setRuleConfig(null);
        when(mapper.updateById(any())).thenReturn(1);

        boolean ok = service.update(tc);

        assertTrue(ok);
        assertEquals("{}", tc.getRuleConfig());
        verify(taskStockService, times(1)).deleteById(9903L);
    }

    @Test
    public void getTaskConfigsByIds_shouldFallbackToDb_whenMultiGetReturnsNull() {
        Set<Long> ids = new LinkedHashSet<>(Arrays.asList(9201L, 9202L));
        List<String> keys = Arrays.asList(CacheKeys.TASK_CONFIG_PREFIX + 9201L, CacheKeys.TASK_CONFIG_PREFIX + 9202L);
        when(redisTemplate.opsForValue().multiGet(keys)).thenReturn(null);

        TaskConfig c1 = new TaskConfig(); c1.setId(9201L);
        TaskConfig c2 = new TaskConfig(); c2.setId(9202L);
        when(mapper.selectBatchIds(anySet())).thenReturn(Arrays.asList(c1, c2));

        Map<Long, TaskConfig> out = service.getTaskConfigsByIds(ids);

        assertEquals(2, out.size());
        assertTrue(out.containsKey(9201L));
        assertTrue(out.containsKey(9202L));
    }

    @Test
    public void getTaskIdsByEventType_shouldReturnEmpty_whenDbListNull() {
        String evt = "EVT_DB_NULL";
        String key = CacheKeys.EVENT_TASKS_PREFIX + evt;
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.SetOperations<String, String> setOps = mock(org.springframework.data.redis.core.SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(key)).thenReturn(Collections.emptySet());
        when(mapper.selectList(any())).thenReturn(null);

        Set<String> out = service.getTaskIdsByEventType(evt);

        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    public void getTaskConfigsByIds_shouldSkipNullParsedRedisConfigAndUseDb() {
        Set<Long> ids = new LinkedHashSet<>(Arrays.asList(9301L, 9302L));
        List<String> keys = Arrays.asList(CacheKeys.TASK_CONFIG_PREFIX + 9301L, CacheKeys.TASK_CONFIG_PREFIX + 9302L);
        when(redisTemplate.opsForValue().multiGet(keys)).thenReturn(Arrays.asList("null", null));

        TaskConfig db = new TaskConfig();
        db.setId(9302L);
        when(mapper.selectBatchIds(anySet())).thenReturn(List.of(db));

        Map<Long, TaskConfig> out = service.getTaskConfigsByIds(ids);

        assertEquals(1, out.size());
        assertTrue(out.containsKey(9302L));
    }

    @Test
    public void getTaskConfigsByIds_shouldReturnWithoutDb_whenAllFoundFromRedis() {
        Set<Long> ids = new LinkedHashSet<>(Arrays.asList(9401L, 9402L));
        List<String> keys = Arrays.asList(CacheKeys.TASK_CONFIG_PREFIX + 9401L, CacheKeys.TASK_CONFIG_PREFIX + 9402L);
        TaskConfig r1 = new TaskConfig(); r1.setId(9401L);
        TaskConfig r2 = new TaskConfig(); r2.setId(9402L);
        when(redisTemplate.opsForValue().multiGet(keys)).thenReturn(Arrays.asList(JSON.toJSONString(r1), JSON.toJSONString(r2)));

        Map<Long, TaskConfig> out = service.getTaskConfigsByIds(ids);

        assertEquals(2, out.size());
        verify(mapper, never()).selectBatchIds(anySet());
    }

    @Test
    public void getTaskConfigsByIds_shouldIgnoreNullDbRowsAndNullIds() {
        Set<Long> ids = new LinkedHashSet<>(Arrays.asList(9501L, 9502L));
        List<String> keys = Arrays.asList(CacheKeys.TASK_CONFIG_PREFIX + 9501L, CacheKeys.TASK_CONFIG_PREFIX + 9502L);
        when(redisTemplate.opsForValue().multiGet(keys)).thenReturn(Arrays.asList(null, null));

        TaskConfig nullId = new TaskConfig();
        nullId.setId(null);
        when(mapper.selectBatchIds(anySet())).thenReturn(Arrays.asList(null, nullId));

        Map<Long, TaskConfig> out = service.getTaskConfigsByIds(ids);

        assertTrue(out.isEmpty());
    }

    @Test
    public void createTaskStock_shouldUseElseIfLimited_whenStairRuleConfigNull() throws Exception {
        java.lang.reflect.Method m = TaskConfigServiceImpl.class.getDeclaredMethod("createTaskStock", TaskConfig.class);
        m.setAccessible(true);

        TaskConfig stairLimitedNoRule = new TaskConfig();
        stairLimitedNoRule.setId(9904L);
        stairLimitedNoRule.setTaskType("STAIR");
        stairLimitedNoRule.setStockType("LIMITED");
        stairLimitedNoRule.setTotalStock(6);
        stairLimitedNoRule.setRuleConfig(null);

        m.invoke(service, stairLimitedNoRule);

        verify(taskStockService, times(1)).save(any());
    }

    @Test
    public void getTaskConfigsByIds_shouldFallbackToDb_whenRedisMultiGetThrows() {
        Set<Long> ids = new LinkedHashSet<>(Arrays.asList(9801L, 9802L));
        List<String> keys = Arrays.asList(CacheKeys.TASK_CONFIG_PREFIX + 9801L, CacheKeys.TASK_CONFIG_PREFIX + 9802L);
        when(redisTemplate.opsForValue().multiGet(keys)).thenThrow(new RuntimeException("redis multiGet down"));

        TaskConfig c1 = new TaskConfig(); c1.setId(9801L);
        TaskConfig c2 = new TaskConfig(); c2.setId(9802L);
        when(mapper.selectBatchIds(anySet())).thenReturn(Arrays.asList(c1, c2));

        Map<Long, TaskConfig> out = service.getTaskConfigsByIds(ids);

        assertEquals(2, out.size());
        assertTrue(out.containsKey(9801L));
        assertTrue(out.containsKey(9802L));
    }

    @Test
    public void getTaskIdsByEventType_shouldReturnEmpty_whenDbQueryThrows() {
        String evt = "EVT_DB_THROW";
        String key = CacheKeys.EVENT_TASKS_PREFIX + evt;
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.SetOperations<String, String> setOps = mock(org.springframework.data.redis.core.SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(key)).thenReturn(Collections.emptySet());
        when(mapper.selectList(any())).thenThrow(new RuntimeException("db down"));

        Set<String> out = service.getTaskIdsByEventType(evt);

        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    public void getTaskConfigsByIds_shouldReturnCurrentResult_whenDbBatchThrows() {
        Set<Long> ids = new LinkedHashSet<>(Arrays.asList(9811L, 9812L));
        List<String> keys = Arrays.asList(CacheKeys.TASK_CONFIG_PREFIX + 9811L, CacheKeys.TASK_CONFIG_PREFIX + 9812L);

        TaskConfig fromRedis = new TaskConfig();
        fromRedis.setId(9811L);
        when(redisTemplate.opsForValue().multiGet(keys)).thenReturn(Arrays.asList(JSON.toJSONString(fromRedis), null));
        when(mapper.selectBatchIds(anySet())).thenThrow(new RuntimeException("db batch down"));

        Map<Long, TaskConfig> out = service.getTaskConfigsByIds(ids);

        assertEquals(1, out.size());
        assertTrue(out.containsKey(9811L));
    }

    @Test
    public void getTaskConfig_shouldThrow_whenRedisReadThrows() {
        Long id = 9821L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(valueOps.get(key)).thenThrow(new RuntimeException("redis get fail"));

        assertThrows(RuntimeException.class, () -> service.getTaskConfig(id));
        verify(mapper, never()).selectById(anyLong());
    }

    @Test
    public void refreshTaskConfig_shouldInvalidateLocalCache_whenDbMissing() throws Exception {
        Long id = 9822L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(valueOps.get(key)).thenReturn(null);
        when(mapper.selectById(id)).thenReturn(null);

        java.lang.reflect.Field cacheField = TaskConfigServiceImpl.class.getDeclaredField("localTaskConfigCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<Long, TaskConfig> localCache = (Cache<Long, TaskConfig>) cacheField.get(service);
        TaskConfig cached = new TaskConfig();
        cached.setId(id);
        localCache.put(id, cached);

        service.refreshTaskConfig(id);

        assertNull(localCache.getIfPresent(id));
        verify(valueOps, never()).set(eq(key), anyString(), anyLong(), any());
    }

    @Test
    public void delegateMethods_shouldCoverSelectAndSearchBranches() {
        TaskConfig row = new TaskConfig();
        row.setId(99001L);
        when(mapper.selectByTaskName("abc")).thenReturn(List.of(row));
        assertEquals(1, service.selectByTaskName("abc").size());

        Page<TaskConfig> p1 = new Page<>(1, 10);
        when(mapper.selectByTaskTypePage(eq("ACCUMULATE"), eq(p1))).thenReturn(List.of(row));
        Page<TaskConfig> typeOut = service.selectByTaskTypePage(p1, "ACCUMULATE");
        assertEquals(1, typeOut.getRecords().size());

        Page<TaskConfig> p2 = new Page<>(1, 10);
        when(mapper.selectByStatusPage(eq(1), eq(p2))).thenReturn(List.of(row));
        Page<TaskConfig> statusOut = service.selectByStatusPage(p2, 1);
        assertEquals(1, statusOut.getRecords().size());

        Page<TaskConfig> p3 = new Page<>(1, 10);
        when(mapper.selectPage(eq(p3), any())).thenReturn(p3);
        Page<TaskConfig> searchOut = service.searchByConditions("name", "ACCUMULATE", 1, "POINT", p3);
        assertSame(p3, searchOut);
    }

    @Test
    public void invalidateTaskConfig_shouldRemoveEntryFromLocalCache() throws Exception {
        Long id = 99002L;
        java.lang.reflect.Field cacheField = TaskConfigServiceImpl.class.getDeclaredField("localTaskConfigCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<Long, TaskConfig> localCache = (Cache<Long, TaskConfig>) cacheField.get(service);
        TaskConfig cached = new TaskConfig();
        cached.setId(id);
        localCache.put(id, cached);

        service.invalidateTaskConfig(id);

        assertNull(localCache.getIfPresent(id));
    }

    @Test
    public void save_shouldReturnFalse_whenInsertFails_andSkipSideEffects() {
        TaskConfig tc = new TaskConfig();
        tc.setStockType("LIMITED");
        tc.setRuleConfig("{}");
        when(mapper.insert(any())).thenReturn(0);

        boolean ok = service.save(tc);

        assertFalse(ok);
        verify(taskStockService, never()).save(any());
        verify(taskStockService, never()).deleteById(anyLong());
        verify(valueOps, never()).set(startsWith(CacheKeys.TASK_CONFIG_PREFIX), anyString(), anyLong(), any());
        verify(stringRedisTemplate.opsForValue(), never()).set(startsWith("task_config_create_time:"), anyString());
    }

    @Test
    public void update_shouldReturnFalse_whenUpdateByIdFails_andSkipSideEffects() {
        TaskConfig tc = new TaskConfig();
        tc.setId(9910L);
        tc.setStockType("LIMITED");
        tc.setRuleConfig("{}");
        when(mapper.updateById(any())).thenReturn(0);

        boolean ok = service.update(tc);

        assertFalse(ok);
        verify(taskStockService, never()).update(any());
        verify(taskStockService, never()).deleteById(anyLong());
        verify(valueOps, never()).set(eq(CacheKeys.TASK_CONFIG_PREFIX + 9910L), anyString(), anyLong(), any());
        verify(stringRedisTemplate.opsForValue(), never()).set(startsWith("task_config_create_time:"), anyString());
    }

    @Test
    public void evictCacheAfterCommit_shouldDeleteRedisAndLocalCache_whenNoSynchronization() throws Exception {
        Long id = 9912L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;

        java.lang.reflect.Field cacheField = TaskConfigServiceImpl.class.getDeclaredField("localTaskConfigCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<Long, TaskConfig> localCache = (Cache<Long, TaskConfig>) cacheField.get(service);
        TaskConfig cached = new TaskConfig();
        cached.setId(id);
        localCache.put(id, cached);

        java.lang.reflect.Method evict = TaskConfigServiceImpl.class.getDeclaredMethod("evictCacheAfterCommit", Long.class);
        evict.setAccessible(true);
        evict.invoke(service, id);

        verify(redisTemplate, times(1)).delete(key);
        assertNull(localCache.getIfPresent(id));
    }

    @Test
    public void evictCacheAfterCommit_shouldKeepFlow_whenRedisDeleteThrows() throws Exception {
        Long id = 9911L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        doThrow(new RuntimeException("redis delete fail")).when(redisTemplate).delete(key);

        java.lang.reflect.Method evict = TaskConfigServiceImpl.class.getDeclaredMethod("evictCacheAfterCommit", Long.class);
        evict.setAccessible(true);

        assertDoesNotThrow(() -> evict.invoke(service, id));
        verify(redisTemplate, times(1)).delete(key);
    }

    @Test
    public void refreshTaskConfig_shouldFallbackToDb_whenRedisJsonInvalid() {
        Long id = 9913L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(valueOps.get(key)).thenReturn("{bad-json");

        TaskConfig db = new TaskConfig();
        db.setId(id);
        db.setTaskName("from-db");
        when(mapper.selectById(id)).thenReturn(db);

        service.refreshTaskConfig(id);

        verify(mapper, times(1)).selectById(id);
        verify(valueOps, times(1)).set(eq(key), anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    public void searchByConditions_shouldHandleNullAndBlankFilters() {
        Page<TaskConfig> p = new Page<>(1, 10);
        when(mapper.selectPage(eq(p), any())).thenReturn(p);

        Page<TaskConfig> out = service.searchByConditions("", null, null, "  ", p);

        assertSame(p, out);
        verify(mapper, times(1)).selectPage(eq(p), any());
    }

    @Test
    public void getTaskConfigsByIds_shouldReturnEmpty_whenDbBatchReturnsNull() {
        Set<Long> ids = new LinkedHashSet<>(Arrays.asList(9921L, 9922L));
        List<String> keys = Arrays.asList(CacheKeys.TASK_CONFIG_PREFIX + 9921L, CacheKeys.TASK_CONFIG_PREFIX + 9922L);
        when(redisTemplate.opsForValue().multiGet(keys)).thenReturn(Arrays.asList(null, null));
        when(mapper.selectBatchIds(anySet())).thenReturn(null);

        Map<Long, TaskConfig> out = service.getTaskConfigsByIds(ids);

        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    public void getTaskConfigsByIds_shouldReturnEmpty_whenDbBatchReturnsEmptyList() {
        Set<Long> ids = new LinkedHashSet<>(Arrays.asList(9931L, 9932L));
        List<String> keys = Arrays.asList(CacheKeys.TASK_CONFIG_PREFIX + 9931L, CacheKeys.TASK_CONFIG_PREFIX + 9932L);
        when(redisTemplate.opsForValue().multiGet(keys)).thenReturn(Arrays.asList(null, null));
        when(mapper.selectBatchIds(anySet())).thenReturn(Collections.emptyList());

        Map<Long, TaskConfig> out = service.getTaskConfigsByIds(ids);

        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    public void createTaskStock_shouldNoop_whenStockTypeNullOrUnlimited() throws Exception {
        java.lang.reflect.Method m = TaskConfigServiceImpl.class.getDeclaredMethod("createTaskStock", TaskConfig.class);
        m.setAccessible(true);

        TaskConfig nullStockType = new TaskConfig();
        nullStockType.setId(9941L);
        nullStockType.setTaskType("ACCUMULATE");
        nullStockType.setStockType(null);
        nullStockType.setTotalStock(3);

        TaskConfig unlimited = new TaskConfig();
        unlimited.setId(9942L);
        unlimited.setTaskType("ACCUMULATE");
        unlimited.setStockType("UNLIMITED");
        unlimited.setTotalStock(3);

        m.invoke(service, nullStockType);
        m.invoke(service, unlimited);

        verify(taskStockService, never()).save(any());
        verify(taskStockService, never()).deleteById(anyLong());
    }

    @Test
    public void getTaskConfig_shouldReturnDb_whenRedisWriteBackThrows() throws Exception {
        Long id = 9951L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(valueOps.get(key)).thenReturn(null);
        TaskConfig db = new TaskConfig();
        db.setId(id);
        db.setTaskName("db-fallback");
        when(mapper.selectById(id)).thenReturn(db);
        doThrow(new RuntimeException("redis set fail"))
                .when(valueOps).set(eq(key), anyString(), eq(60L), eq(TimeUnit.SECONDS));

        TaskConfig out = service.getTaskConfig(id);

        assertNotNull(out);
        assertEquals(id, out.getId());

        java.lang.reflect.Field cacheField = TaskConfigServiceImpl.class.getDeclaredField("localTaskConfigCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<Long, TaskConfig> localCache = (Cache<Long, TaskConfig>) cacheField.get(service);
        assertNotNull(localCache.getIfPresent(id));
    }

    @Test
    public void refreshTaskConfig_shouldKeepLocalCache_whenRedisWriteBackThrows() throws Exception {
        Long id = 9961L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(valueOps.get(key)).thenReturn(null);
        TaskConfig db = new TaskConfig();
        db.setId(id);
        db.setTaskName("db-refresh");
        when(mapper.selectById(id)).thenReturn(db);
        doThrow(new RuntimeException("redis set fail"))
                .when(valueOps).set(eq(key), anyString(), eq(60L), eq(TimeUnit.SECONDS));

        service.refreshTaskConfig(id);

        java.lang.reflect.Field cacheField = TaskConfigServiceImpl.class.getDeclaredField("localTaskConfigCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<Long, TaskConfig> localCache = (Cache<Long, TaskConfig>) cacheField.get(service);
        assertNotNull(localCache.getIfPresent(id));
    }

    @Test
    public void writeCacheAfterCommit_shouldKeepFlow_whenNoSynchronizationAndRedisWriteThrows() throws Exception {
        Long id = 9971L;
        TaskConfig tc = new TaskConfig();
        tc.setId(id);
        tc.setTaskName("no-sync-write");

        doThrow(new RuntimeException("redis set fail"))
                .when(valueOps).set(startsWith(CacheKeys.TASK_CONFIG_PREFIX), anyString(), eq(60L), eq(TimeUnit.SECONDS));

        java.lang.reflect.Method write = TaskConfigServiceImpl.class.getDeclaredMethod("writeCacheAfterCommit", TaskConfig.class);
        write.setAccessible(true);
        assertDoesNotThrow(() -> write.invoke(service, tc));

        java.lang.reflect.Field cacheField = TaskConfigServiceImpl.class.getDeclaredField("localTaskConfigCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<Long, TaskConfig> localCache = (Cache<Long, TaskConfig>) cacheField.get(service);
        assertNotNull(localCache.getIfPresent(id));
    }

    @Test
    public void deleteById_shouldReturnFalse_whenMapperDeleteFails() {
        when(mapper.deleteById(9981L)).thenReturn(0);

        boolean ok = service.deleteById(9981L);

        assertFalse(ok);
        verify(taskStockService, never()).deleteById(anyLong());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    public void deleteById_shouldDeleteStockAndEvictCacheImmediately_whenNoTransaction() throws Exception {
        Long id = 9982L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(mapper.deleteById(id)).thenReturn(1);

        java.lang.reflect.Field cacheField = TaskConfigServiceImpl.class.getDeclaredField("localTaskConfigCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<Long, TaskConfig> localCache = (Cache<Long, TaskConfig>) cacheField.get(service);
        TaskConfig cached = new TaskConfig();
        cached.setId(id);
        localCache.put(id, cached);

        boolean ok = service.deleteById(id);

        assertTrue(ok);
        verify(taskStockService, times(1)).deleteById(id);
        verify(redisTemplate, times(1)).delete(key);
        assertNull(localCache.getIfPresent(id));
    }

    @Test
    public void deleteById_shouldRunSideEffectsAfterCommit_whenTransactionActive() {
        Long id = 9983L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(mapper.deleteById(id)).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            boolean ok = service.deleteById(id);
            assertTrue(ok);
            verify(taskStockService, never()).deleteById(id);
            verify(redisTemplate, never()).delete(key);

            for (org.springframework.transaction.support.TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(taskStockService, times(1)).deleteById(id);
            verify(redisTemplate, times(1)).delete(key);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void deleteById_shouldKeepSuccess_whenSideEffectsThrow_noTransaction() {
        Long id = 9984L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(mapper.deleteById(id)).thenReturn(1);
        doThrow(new RuntimeException("stock delete fail")).when(taskStockService).deleteById(id);
        doThrow(new RuntimeException("redis delete fail")).when(redisTemplate).delete(key);

        boolean ok = service.deleteById(id);

        assertTrue(ok);
        verify(taskStockService, times(1)).deleteById(id);
        verify(redisTemplate, times(1)).delete(key);
    }

    @Test
    public void deleteById_shouldKeepSuccess_whenSideEffectsThrow_afterCommit() {
        Long id = 9985L;
        String key = CacheKeys.TASK_CONFIG_PREFIX + id;
        when(mapper.deleteById(id)).thenReturn(1);
        doThrow(new RuntimeException("stock delete fail")).when(taskStockService).deleteById(id);
        doThrow(new RuntimeException("redis delete fail")).when(redisTemplate).delete(key);

        TransactionSynchronizationManager.initSynchronization();
        try {
            boolean ok = service.deleteById(id);
            assertTrue(ok);

            for (org.springframework.transaction.support.TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                assertDoesNotThrow(sync::afterCommit);
            }

            verify(taskStockService, times(1)).deleteById(id);
            verify(redisTemplate, times(1)).delete(key);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void warmupAllTaskConfigs_shouldPopulateLocalAndRedisAndEventSets() throws Exception {
        TaskConfig c1 = new TaskConfig();
        c1.setId(11001L);
        c1.setTaskName("t1");
        c1.setTriggerEvent("USER_LEARN");
        TaskConfig c2 = new TaskConfig();
        c2.setId(11002L);
        c2.setTaskName("t2");
        c2.setTriggerEvent("USER_SIGN");
        when(mapper.selectList(null)).thenReturn(List.of(c1, c2));

        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.SetOperations<String, String> setOps = mock(org.springframework.data.redis.core.SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        int warmed = service.warmupAllTaskConfigs(1, 120);

        assertEquals(2, warmed);
        verify(valueOps, times(2)).set(startsWith(CacheKeys.TASK_CONFIG_PREFIX), anyString(), eq(120L), eq(TimeUnit.SECONDS));
        verify(redisTemplate, atLeastOnce()).opsForSet();

        java.lang.reflect.Field cacheField = TaskConfigServiceImpl.class.getDeclaredField("localTaskConfigCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<Long, TaskConfig> localCache = (Cache<Long, TaskConfig>) cacheField.get(service);
        assertNotNull(localCache.getIfPresent(11001L));
        assertNotNull(localCache.getIfPresent(11002L));
    }

    @Test
    public void warmupAllTaskConfigs_shouldReturnZero_whenNoTaskConfigs() {
        when(mapper.selectList(null)).thenReturn(Collections.emptyList());

        int warmed = service.warmupAllTaskConfigs(100, 60);

        assertEquals(0, warmed);
        verify(valueOps, never()).set(startsWith(CacheKeys.TASK_CONFIG_PREFIX), anyString(), anyLong(), any());
    }
}
