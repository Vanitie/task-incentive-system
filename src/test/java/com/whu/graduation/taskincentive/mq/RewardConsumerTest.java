package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
import com.whu.graduation.taskincentive.strategy.reward.RewardStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardConsumerTest {

    @Mock
    private RewardStrategy rewardStrategy;
    @Mock
    private UserRewardRecordService recordService;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ErrorPublisher errorPublisher;

    private RewardConsumer consumer;

    @BeforeEach
    void setUp() {
        when(rewardStrategy.getType()).thenReturn(RewardType.POINT);
        consumer = new RewardConsumer(List.of(rewardStrategy), recordService);
        ReflectionTestUtils.setField(consumer, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(consumer, "errorPublisher", errorPublisher);
        consumer.init();
    }

    @Test
    void consume_shouldSendDlqForInvalidJson() {
        consumer.consume("{bad-json");

        verify(errorPublisher).publishToDlq(eq("reward-topic"), anyString(), eq((String) null), anyString(), any(Map.class));
        verify(recordService, never()).markProcessing(anyString());
    }

    @Test
    void consume_shouldSendDlqForEmptyJsonObject() {
        consumer.consume("null");

        verify(errorPublisher).publishToDlq(eq("reward-topic"), eq("null"), eq((String) null), anyString(), any(Map.class));
        verify(recordService, never()).markProcessing(anyString());
    }

    @Test
    void consume_shouldSendDlqWhenRequiredFieldsMissing() {
        consumer.consume("{\"messageId\":\"m-missing\",\"reward\":{\"rewardType\":\"POINT\"}}");

        verify(errorPublisher).publishToDlq(eq("reward-topic"), anyString(), eq("m-missing"), eq("invalid reward message"), any(Map.class));
        verify(recordService, never()).markProcessing(anyString());
    }

    @Test
    void consume_shouldSkipWhenExistingSuccessRecord() {
        UserRewardRecord existing = new UserRewardRecord();
        existing.setGrantStatus(2);
        when(recordService.initRecordIfAbsent(eq("m-1"), eq(1L), any(Reward.class))).thenReturn(existing);

        consumer.consume("{\"messageId\":\"m-1\",\"userId\":1,\"reward\":{\"rewardType\":\"POINT\",\"amount\":10}}");

        verify(recordService, never()).markProcessing("m-1");
        verify(rewardStrategy, never()).grantReward(anyLong(), any());
    }

    @Test
    void consume_shouldSkipWhenMarkProcessingFalseAndLatestSuccess() {
        when(recordService.initRecordIfAbsent(eq("m-10"), eq(10L), any(Reward.class))).thenReturn(null);
        when(recordService.markProcessing("m-10")).thenReturn(false);
        UserRewardRecord latest = new UserRewardRecord();
        latest.setGrantStatus(2);
        when(recordService.selectByMessageId("m-10")).thenReturn(latest);

        consumer.consume("{\"messageId\":\"m-10\",\"userId\":10,\"reward\":{\"rewardType\":\"POINT\",\"amount\":1}}");

        verify(recordService).selectByMessageId("m-10");
        verify(rewardStrategy, never()).grantReward(anyLong(), any());
    }

    @Test
    void consume_shouldMarkFailedWhenUnknownRewardType() {
        when(recordService.initRecordIfAbsent(eq("m-2"), eq(1L), any(Reward.class))).thenReturn(null);
        when(recordService.markProcessing("m-2")).thenReturn(true);

        consumer.consume("{\"messageId\":\"m-2\",\"userId\":1,\"reward\":{\"rewardType\":\"BADGE\",\"amount\":1}}");

        verify(recordService).markFailedNewTx(eq("m-2"), anyString());
        verify(rewardStrategy, never()).grantReward(anyLong(), any());
    }

    @Test
    void consume_shouldProcessAndSetDedupWhenMessageIdPresent() {
        when(recordService.initRecordIfAbsent(eq("m-3"), eq(5L), any(Reward.class))).thenReturn(null);
        when(recordService.markProcessing("m-3")).thenReturn(true);
        when(recordService.markSuccess("m-3")).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("mq:processed:m-3"), eq("1"), anyLong(), eq(TimeUnit.HOURS))).thenReturn(true);

        consumer.consume("{\"messageId\":\"m-3\",\"userId\":5,\"reward\":{\"rewardType\":\"POINT\",\"amount\":8,\"taskId\":100}}");

        verify(rewardStrategy).grantReward(eq(5L), any(Reward.class));
        verify(recordService).markSuccess("m-3");
        verify(valueOperations).setIfAbsent(eq("mq:processed:m-3"), eq("1"), eq(6L), eq(TimeUnit.HOURS));
        verify(recordService, never()).save(any(UserRewardRecord.class));
    }

    @Test
    void consume_shouldSaveRecordWhenMessageIdMissing() {
        consumer.consume("{\"userId\":6,\"reward\":{\"rewardType\":\"POINT\",\"amount\":2,\"taskId\":101,\"rewardId\":88}}");

        verify(rewardStrategy).grantReward(eq(6L), any(Reward.class));
        verify(recordService).save(any(UserRewardRecord.class));
    }

    @Test
    void consume_shouldMarkFailedAndPublishDlqOnStrategyError() {
        when(recordService.initRecordIfAbsent(eq("m-9"), eq(9L), any(Reward.class))).thenReturn(null);
        when(recordService.markProcessing("m-9")).thenReturn(true);
        when(rewardStrategy.grantReward(eq(9L), any(Reward.class))).thenThrow(new RuntimeException("grant error"));

        String message = JSON.toJSONString(Map.of(
                "messageId", "m-9",
                "userId", 9,
                "reward", Map.of("rewardType", "POINT", "amount", 3)
        ));

        assertThrows(RuntimeException.class, () -> consumer.consume(message));

        verify(recordService).markFailedNewTx(eq("m-9"), anyString());
        verify(errorPublisher).publishToDlq(eq("reward-topic"), eq(message), eq("m-9"), anyString(), any(Map.class));
    }
}

