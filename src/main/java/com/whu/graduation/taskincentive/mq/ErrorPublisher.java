package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ErrorPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final String dlqTopic;

    public ErrorPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.dlqTopic = CacheKeys.DEFAULT_DLQ_TOPIC;
    }

    public void publishToDlq(String originalTopic, String message) {
        publishToDlq(originalTopic, message, null);
    }

    public void publishToDlq(String originalTopic, String message, String messageId) {
        publishToDlq(originalTopic, message, messageId, null);
    }

    public void publishToDlq(String originalTopic, String message, String messageId, String error) {
        try {
            JSONObject wrapper = new JSONObject();
            wrapper.put("origTopic", originalTopic);
            wrapper.put("payload", message);
            wrapper.put("messageId", messageId);
            wrapper.put("error", error);
            wrapper.put("ts", System.currentTimeMillis());
            kafkaTemplate.send(dlqTopic, wrapper.toJSONString());
            log.warn("message published to DLQ topic={}, origTopic={}, messageId={}", dlqTopic, originalTopic, messageId);
        } catch (Exception e) {
            log.error("failed to publish message to DLQ topic={}, err={}", dlqTopic, e.getMessage());
        }
    }
}
