package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskPersistenceConsumerTest {

    @Mock
    private UserTaskInstanceService instanceService;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ErrorPublisher errorPublisher;
    @Mock
    private Acknowledgment acknowledgment;

    private TaskPersistenceConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TaskPersistenceConsumer();
        ReflectionTestUtils.setField(consumer, "instanceService", instanceService);
        ReflectionTestUtils.setField(consumer, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(consumer, "errorPublisher", errorPublisher);
    }

    @Test
    void consume_shouldAckAndReturnWhenDuplicateDetected() {
        when(redisTemplate.hasKey("mq:processed:m-dup")).thenReturn(true);

        consumer.consume("{\"messageId\":\"m-dup\",\"payload\":{\"id\":1}}", acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(instanceService, never()).updateWithVersion(any());
    }

    @Test
    void consume_shouldSendDlqAndAckOnPayloadParseError() {
        consumer.consume("{bad-json", acknowledgment);

        verify(errorPublisher).publishToDlq(eq("task-persist-topic"), anyString(), any(), anyString(), any(Map.class));
        verify(acknowledgment).acknowledge();
        verify(instanceService, never()).updateWithVersion(any());
    }

    @Test
    void consume_shouldRetryWithLatestVersionAndSetDedupOnSuccess() {
        UserTaskInstance payload = new UserTaskInstance();
        payload.setId(100L);
        payload.setVersion(1);

        UserTaskInstance latest = new UserTaskInstance();
        latest.setId(100L);
        latest.setVersion(5);

        String wrapped = "{\"messageId\":\"m-ok\",\"payload\":" + JSON.toJSONString(payload) + "}";

        when(redisTemplate.hasKey("mq:processed:m-ok")).thenReturn(false);
        when(instanceService.updateWithVersion(any(UserTaskInstance.class))).thenReturn(0).thenReturn(1);
        when(instanceService.getById(100L)).thenReturn(latest);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("mq:processed:m-ok"), eq("1"), anyLong(), eq(TimeUnit.DAYS))).thenReturn(true);

        consumer.consume(wrapped, acknowledgment);

        verify(instanceService, times(2)).updateWithVersion(any(UserTaskInstance.class));
        verify(instanceService).getById(100L);
        verify(valueOperations).setIfAbsent(eq("mq:processed:m-ok"), eq("1"), eq(7L), eq(TimeUnit.DAYS));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_shouldPublishDlqAndRethrowOnPersistenceError() {
        UserTaskInstance payload = new UserTaskInstance();
        payload.setId(300L);
        String wrapped = "{\"messageId\":\"m-fail\",\"payload\":" + JSON.toJSONString(payload) + "}";

        when(redisTemplate.hasKey("mq:processed:m-fail")).thenReturn(false);
        when(instanceService.updateWithVersion(any(UserTaskInstance.class))).thenThrow(new RuntimeException("db error"));

        assertThrows(RuntimeException.class, () -> consumer.consume(wrapped, acknowledgment));

        verify(errorPublisher).publishToDlq(eq("task-persist-topic"), anyString(), eq("m-fail"), anyString(), any(Map.class));
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consume_shouldSupportDataWrapper_andAck() {
        UserTaskInstance payload = new UserTaskInstance();
        payload.setId(401L);
        String wrapped = "{\"messageId\":\"m-data\",\"data\":" + JSON.toJSONString(payload) + "}";

        when(redisTemplate.hasKey("mq:processed:m-data")).thenReturn(false);
        when(instanceService.updateWithVersion(any(UserTaskInstance.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("mq:processed:m-data"), eq("1"), anyLong(), eq(TimeUnit.DAYS))).thenReturn(true);

        consumer.consume(wrapped, acknowledgment);

        verify(instanceService).updateWithVersion(any(UserTaskInstance.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_shouldSupportBodyWrapperStringPayload_andAck() {
        UserTaskInstance payload = new UserTaskInstance();
        payload.setId(402L);
        String wrapped = "{\"messageId\":\"m-body\",\"body\":\"" + JSON.toJSONString(payload).replace("\"", "\\\"") + "\"}";

        when(redisTemplate.hasKey("mq:processed:m-body")).thenReturn(false);
        when(instanceService.updateWithVersion(any(UserTaskInstance.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("mq:processed:m-body"), eq("1"), anyLong(), eq(TimeUnit.DAYS))).thenReturn(false);

        consumer.consume(wrapped, acknowledgment);

        verify(instanceService).updateWithVersion(any(UserTaskInstance.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_shouldProceedWhenDedupCheckThrows() {
        UserTaskInstance payload = new UserTaskInstance();
        payload.setId(403L);
        String wrapped = "{\"messageId\":\"m-dedup-err\",\"payload\":" + JSON.toJSONString(payload) + "}";

        when(redisTemplate.hasKey("mq:processed:m-dedup-err")).thenThrow(new RuntimeException("redis down"));
        when(instanceService.updateWithVersion(any(UserTaskInstance.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("mq:processed:m-dedup-err"), eq("1"), anyLong(), eq(TimeUnit.DAYS))).thenReturn(true);

        consumer.consume(wrapped, acknowledgment);

        verify(instanceService).updateWithVersion(any(UserTaskInstance.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_shouldWarnAndAckWhenRetryLatestNotFound() {
        UserTaskInstance payload = new UserTaskInstance();
        payload.setId(404L);
        String wrapped = "{\"messageId\":\"m-no-latest\",\"payload\":" + JSON.toJSONString(payload) + "}";

        when(redisTemplate.hasKey("mq:processed:m-no-latest")).thenReturn(false);
        when(instanceService.updateWithVersion(any(UserTaskInstance.class))).thenReturn(0);
        when(instanceService.getById(404L)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("mq:processed:m-no-latest"), eq("1"), anyLong(), eq(TimeUnit.DAYS))).thenReturn(true);

        consumer.consume(wrapped, acknowledgment);

        verify(instanceService, times(1)).updateWithVersion(any(UserTaskInstance.class));
        verify(instanceService).getById(404L);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_shouldIgnoreDedupMarkSetFailure_andAck() {
        UserTaskInstance payload = new UserTaskInstance();
        payload.setId(405L);
        String wrapped = "{\"messageId\":\"m-set-fail\",\"payload\":" + JSON.toJSONString(payload) + "}";

        when(redisTemplate.hasKey("mq:processed:m-set-fail")).thenReturn(false);
        when(instanceService.updateWithVersion(any(UserTaskInstance.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("mq:processed:m-set-fail"), eq("1"), anyLong(), eq(TimeUnit.DAYS)))
                .thenThrow(new RuntimeException("set fail"));

        consumer.consume(wrapped, acknowledgment);

        verify(instanceService).updateWithVersion(any(UserTaskInstance.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_shouldProcessRawPayloadWithoutMessageId() {
        UserTaskInstance payload = new UserTaskInstance();
        payload.setId(406L);

        when(instanceService.updateWithVersion(any(UserTaskInstance.class))).thenReturn(1);

        consumer.consume(JSON.toJSONString(payload), acknowledgment);

        verify(redisTemplate, never()).hasKey(anyString());
        verify(redisTemplate, never()).opsForValue();
        verify(instanceService).updateWithVersion(any(UserTaskInstance.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_shouldPublishDlqAndRethrow_whenSecondUpdateFailsAfterVersionRetry() {
        UserTaskInstance payload = new UserTaskInstance();
        payload.setId(407L);
        payload.setVersion(1);
        UserTaskInstance latest = new UserTaskInstance();
        latest.setId(407L);
        latest.setVersion(9);
        String wrapped = "{\"messageId\":\"m-retry-fail\",\"payload\":" + JSON.toJSONString(payload) + "}";

        when(redisTemplate.hasKey("mq:processed:m-retry-fail")).thenReturn(false);
        when(instanceService.updateWithVersion(any(UserTaskInstance.class))).thenReturn(0).thenThrow(new RuntimeException("retry update failed"));
        when(instanceService.getById(407L)).thenReturn(latest);

        assertThrows(RuntimeException.class, () -> consumer.consume(wrapped, acknowledgment));

        verify(instanceService, times(2)).updateWithVersion(any(UserTaskInstance.class));
        verify(errorPublisher).publishToDlq(eq("task-persist-topic"), anyString(), eq("m-retry-fail"), anyString(), any(Map.class));
        verify(acknowledgment, never()).acknowledge();
    }
}


