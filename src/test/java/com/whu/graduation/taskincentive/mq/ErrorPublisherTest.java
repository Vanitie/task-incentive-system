package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ErrorPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void publishToDlq_shouldWrapPayloadAndSendToDefaultTopic() {
        ErrorPublisher publisher = new ErrorPublisher(kafkaTemplate);

        publisher.publishToDlq("reward-topic", "raw-msg", "m-1", "boom", Map.of("source", "test"));

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(CacheKeys.DEFAULT_DLQ_TOPIC), msgCaptor.capture());

        JSONObject wrapper = JSON.parseObject(msgCaptor.getValue());
        assertEquals("reward-topic", wrapper.getString("origTopic"));
        assertEquals("raw-msg", wrapper.getString("payload"));
        assertEquals("m-1", wrapper.getString("messageId"));
        assertEquals("boom", wrapper.getString("error"));
        assertNotNull(wrapper.getLong("ts"));
        assertEquals("test", wrapper.getJSONObject("meta").getString("source"));
    }

    @Test
    void publishToDlq_overloadShouldWorkWithoutMeta() {
        ErrorPublisher publisher = new ErrorPublisher(kafkaTemplate);

        publisher.publishToDlq("task-topic", "body-only");

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(CacheKeys.DEFAULT_DLQ_TOPIC), msgCaptor.capture());

        JSONObject wrapper = JSON.parseObject(msgCaptor.getValue());
        assertEquals("task-topic", wrapper.getString("origTopic"));
        assertEquals("body-only", wrapper.getString("payload"));
        // fastjson may omit null fields, so we only assert essential envelope fields.
        assertTrue(wrapper.containsKey("ts"));
    }

    @Test
    void publishToDlq_shouldSkipMeta_whenEmptyMap() {
        ErrorPublisher publisher = new ErrorPublisher(kafkaTemplate);

        publisher.publishToDlq("reward-topic", "raw-msg", "m-2", "boom", Map.of());

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(CacheKeys.DEFAULT_DLQ_TOPIC), msgCaptor.capture());

        JSONObject wrapper = JSON.parseObject(msgCaptor.getValue());
        assertTrue(wrapper.containsKey("ts"));
        assertTrue(!wrapper.containsKey("meta"));
    }

    @Test
    void publishToDlq_shouldSwallowKafkaException() {
        doThrow(new RuntimeException("kafka down")).when(kafkaTemplate).send(anyString(), anyString());
        ErrorPublisher publisher = new ErrorPublisher(kafkaTemplate);

        assertDoesNotThrow(() -> publisher.publishToDlq("x", "y", "id-x", "err-x"));
    }
}



