package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.testutil.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RewardProducerTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private RewardProducer producer;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        producer = new RewardProducer();
        ReflectionTestUtils.setFieldRecursively(producer, "kafkaTemplate", kafkaTemplate);
    }

    @Test
    void sendRewardWithMessageId_shouldGenerateRewardIdAndSend() {
        CompletableFuture<SendResult<String, String>> ok = CompletableFuture.completedFuture(null);
        doReturn(ok).when(kafkaTemplate).send(anyString(), anyString(), anyString());

        Reward reward = Reward.builder().taskId(10L).rewardType(RewardType.POINT).amount(5).build();
        producer.sendRewardWithMessageId(100L, reward, "m-1");

        assertNotNull(reward.getRewardId());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());

        JSONObject payload = JSON.parseObject(payloadCaptor.getValue());
        assertNotNull(payload.getString("messageId"));
        assertNotNull(payload.getJSONObject("reward").getLong("rewardId"));
    }

    @Test
    void sendRewardWithMessageId_shouldThrowWhenKafkaSendFails() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        doReturn(failed).when(kafkaTemplate).send(anyString(), anyString(), anyString());

        assertThrows(IllegalStateException.class,
                () -> producer.sendRewardWithMessageId(101L, Reward.builder().build(), "m-2"));
    }

    @Test
    void sendReward_shouldGenerateMessageIdInternally() {
        CompletableFuture<SendResult<String, String>> ok = CompletableFuture.completedFuture(null);
        doReturn(ok).when(kafkaTemplate).send(anyString(), anyString(), anyString());

        producer.sendReward(102L, Reward.builder().rewardType(RewardType.POINT).amount(1).build());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());
        JSONObject payload = JSON.parseObject(payloadCaptor.getValue());
        assertNotNull(payload.getString("messageId"));
    }

    @Test
    void sendRewardWithMessageId_shouldKeepExistingRewardId() {
        CompletableFuture<SendResult<String, String>> ok = CompletableFuture.completedFuture(null);
        doReturn(ok).when(kafkaTemplate).send(anyString(), anyString(), anyString());

        Reward reward = Reward.builder().rewardId(9999L).rewardType(RewardType.POINT).amount(2).build();
        producer.sendRewardWithMessageId(103L, reward, "m-3");

        assertNotNull(reward.getRewardId());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());
        JSONObject payload = JSON.parseObject(payloadCaptor.getValue());
        assertNotNull(payload.getJSONObject("reward"));
        org.junit.jupiter.api.Assertions.assertEquals(9999L, payload.getJSONObject("reward").getLongValue("rewardId"));
    }

    @Test
    void sendRewardWithMessageId_shouldSendWhenRewardIsNull() {
        CompletableFuture<SendResult<String, String>> ok = CompletableFuture.completedFuture(null);
        doReturn(ok).when(kafkaTemplate).send(anyString(), anyString(), anyString());

        producer.sendRewardWithMessageId(104L, null, "m-4");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());
        JSONObject payload = JSON.parseObject(payloadCaptor.getValue());
        assertNotNull(payload.getString("messageId"));
        assertNull(payload.get("reward"));
    }
}


