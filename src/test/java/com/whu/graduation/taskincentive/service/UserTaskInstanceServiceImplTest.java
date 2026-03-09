package com.whu.graduation.taskincentive.service;

import com.whu.graduation.taskincentive.common.error.BusinessException;
import com.alibaba.fastjson.JSON;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import com.whu.graduation.taskincentive.service.impl.UserTaskInstanceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Date;

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
        verify(valueOps, times(1)).set(anyString(), anyString());
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
        verify(valueOps, times(1)).set(anyString(), anyString());
    }

    @Test
    public void updateAndPublish_writesRedisAndSendsKafka() throws Exception {
        // inject kafkaTemplate mock
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka = mock(org.springframework.kafka.core.KafkaTemplate.class);
        java.lang.reflect.Field fKafka = UserTaskInstanceServiceImpl.class.getDeclaredField("kafkaTemplate");
        fKafka.setAccessible(true);
        fKafka.set(service, kafka);

        UserTaskInstance inst = new UserTaskInstance();
        inst.setId(200L); inst.setUserId(42L); inst.setTaskId(99L); inst.setStatus(1);

        service.updateAndPublish(inst);

        // should write to redis and send kafka message
        verify(valueOps, times(1)).set(anyString(), anyString());
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    public void getOrCreate_fallbacksToSelect_onInsertException() {
        // simulate no existing instance then insert throws, then selectByUserAndTask returns instance
        when(mapper.selectByUserAndTask(7L, 8L)).thenReturn(null).thenReturn(new UserTaskInstance(){ { setUserId(7L); setTaskId(8L); setStatus(1); setId(555L);} });
        when(mapper.insert(any())).thenThrow(new RuntimeException("duplicate key"));

        UserTaskInstance inst = service.getOrCreate(7L, 8L);
        assertNotNull(inst);
        assertEquals(7L, inst.getUserId());
        assertEquals(8L, inst.getTaskId());
    }

    @Test
    public void getOrCreateWithCache_returnsFromRedis_whenPresent() {
        UserTaskInstance cached = new UserTaskInstance();
        cached.setId(888L); cached.setUserId(9L); cached.setTaskId(10L); cached.setStatus(1);
        when(valueOps.get(anyString())).thenReturn(JSON.toJSONString(cached));

        UserTaskInstance res = service.getOrCreateWithCache(9L, 10L);
        assertNotNull(res);
        assertEquals(888L, res.getId());
        // should not call DB
        verify(mapper, never()).selectByUserAndTask(anyLong(), anyLong());
    }

    @Test
    public void acceptTask_throws_whenNotStarted() {
        TaskConfig cfg = new TaskConfig(); cfg.setId(40L); cfg.setStatus(1);
        cfg.setStartTime(new Date(System.currentTimeMillis() + 100000)); // future
        when(taskConfigService.getTaskConfig(40L)).thenReturn(cfg);
        assertThrows(BusinessException.class, () -> service.acceptTask(3L, 40L));
    }

    @Test
    public void acceptTask_throws_whenAlreadyEnded() {
        TaskConfig cfg = new TaskConfig(); cfg.setId(41L); cfg.setStatus(1);
        cfg.setEndTime(new Date(System.currentTimeMillis() - 100000)); // past
        when(taskConfigService.getTaskConfig(41L)).thenReturn(cfg);
        assertThrows(BusinessException.class, () -> service.acceptTask(4L, 41L));
    }

    @Test
    public void updateWithVersion_delegatesToMapper() {
        UserTaskInstance inst = new UserTaskInstance(); inst.setId(123L); inst.setVersion(0);
        when(mapper.updateWithVersion(any())).thenReturn(1);
        int r = service.updateWithVersion(inst);
        assertEquals(1, r);
        verify(mapper, times(1)).updateWithVersion(any());
    }
}
