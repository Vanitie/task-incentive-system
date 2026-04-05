package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RiskDecisionPersistProducerTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private RiskDecisionPersistProducer producer;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        producer = new RiskDecisionPersistProducer(kafkaTemplate);
    }

    @Test
    void send_shouldWrapPayloadAndPublish() {
        CompletableFuture<SendResult<String, String>> ok = CompletableFuture.completedFuture(null);
        doReturn(ok).when(kafkaTemplate).send(anyString(), anyString(), anyString());

        producer.send("user-1", Map.of("requestId", "r-1"));

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), value.capture());

        assertEquals(CacheKeys.RISK_DECISION_PERSIST_TOPIC, topic.getValue());
        assertEquals("user-1", key.getValue());

        JSONObject wrapper = JSON.parseObject(value.getValue());
        assertNotNull(wrapper.getString("messageId"));
        assertEquals("r-1", wrapper.getJSONObject("payload").getString("requestId"));
    }

    @Test
    void send_shouldThrowWhenKafkaSendFails() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        doReturn(failed).when(kafkaTemplate).send(anyString(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> producer.send("user-2", Map.of("k", "v")));
    }
}

