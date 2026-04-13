package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import com.whu.graduation.taskincentive.dao.entity.RewardFreezeRecord;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.dao.mapper.RewardFreezeRecordMapper;
import com.whu.graduation.taskincentive.dao.mapper.RiskDecisionLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

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
class RiskDecisionPersistConsumerTest {

    @Mock
    private RiskDecisionLogMapper decisionLogMapper;
    @Mock
    private RewardFreezeRecordMapper rewardFreezeRecordMapper;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ErrorPublisher errorPublisher;
    @Mock
    private Acknowledgment acknowledgment;

    @Test
    void consume_shouldAckWhenDuplicateMessageDetected() {
        RiskDecisionPersistConsumer consumer = new RiskDecisionPersistConsumer(
                decisionLogMapper, rewardFreezeRecordMapper, redisTemplate, errorPublisher
        );

        when(redisTemplate.hasKey("mq:processed:r-dup")).thenReturn(true);

        consumer.consume("{\"messageId\":\"r-dup\",\"payload\":{}}", acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(decisionLogMapper, never()).insert(any());
    }

    @Test
    void consume_shouldSendDlqAndAckOnPayloadParseError() {
        RiskDecisionPersistConsumer consumer = new RiskDecisionPersistConsumer(
                decisionLogMapper, rewardFreezeRecordMapper, redisTemplate, errorPublisher
        );

        consumer.consume("{bad", acknowledgment);

        verify(errorPublisher).publishToDlq(eq(CacheKeys.RISK_DECISION_PERSIST_TOPIC), anyString(), any(), anyString(), any(Map.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_shouldPersistAndAckOnSuccess() {
        RiskDecisionPersistConsumer consumer = new RiskDecisionPersistConsumer(
                decisionLogMapper, rewardFreezeRecordMapper, redisTemplate, errorPublisher
        );

        RiskDecisionPersistMessage payload = RiskDecisionPersistMessage.builder()
                .decisionLog(RiskDecisionLog.builder().requestId("req-1").build())
                .freezeRecord(RewardFreezeRecord.builder().rewardId(1L).build())
                .build();
        String message = "{\"messageId\":\"r-ok\",\"payload\":" + JSON.toJSONString(payload) + "}";

        when(redisTemplate.hasKey("mq:processed:r-ok")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("mq:processed:r-ok"), eq("1"), anyLong(), eq(TimeUnit.HOURS))).thenReturn(true);

        consumer.consume(message, acknowledgment);

        verify(decisionLogMapper).insert(any(RiskDecisionLog.class));
        verify(rewardFreezeRecordMapper).insert(any(RewardFreezeRecord.class));
        verify(valueOperations).setIfAbsent(eq("mq:processed:r-ok"), eq("1"), eq(6L), eq(TimeUnit.HOURS));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_shouldAckWhenDuplicateKeyExceptionThrown() {
        RiskDecisionPersistConsumer consumer = new RiskDecisionPersistConsumer(
                decisionLogMapper, rewardFreezeRecordMapper, redisTemplate, errorPublisher
        );

        RiskDecisionPersistMessage payload = RiskDecisionPersistMessage.builder()
                .decisionLog(RiskDecisionLog.builder().requestId("dup").build())
                .build();
        String message = JSON.toJSONString(payload);

        when(decisionLogMapper.insert(any(RiskDecisionLog.class))).thenThrow(new DuplicateKeyException("dup"));

        consumer.consume(message, acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_shouldPublishDlqAndRethrowOnUnexpectedError() {
        RiskDecisionPersistConsumer consumer = new RiskDecisionPersistConsumer(
                decisionLogMapper, rewardFreezeRecordMapper, redisTemplate, errorPublisher
        );

        RiskDecisionPersistMessage payload = RiskDecisionPersistMessage.builder()
                .decisionLog(RiskDecisionLog.builder().requestId("req-x").build())
                .build();
        String wrapped = "{\"messageId\":\"r-fail\",\"payload\":" + JSON.toJSONString(payload) + "}";

        when(redisTemplate.hasKey("mq:processed:r-fail")).thenReturn(false);
        when(decisionLogMapper.insert(any(RiskDecisionLog.class))).thenThrow(new RuntimeException("db fail"));

        assertThrows(RuntimeException.class, () -> consumer.consume(wrapped, acknowledgment));

        verify(errorPublisher).publishToDlq(eq(CacheKeys.RISK_DECISION_PERSIST_TOPIC), anyString(), eq("r-fail"), anyString(), any(Map.class));
        verify(acknowledgment, never()).acknowledge();
    }
}

