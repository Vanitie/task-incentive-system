package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 用户行为日志落库生产者
 */
@Slf4j
@Component
public class UserActionLogPersistProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public UserActionLogPersistProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String userKey, UserActionLogPersistMessage payload) {
        String messageId = UUID.randomUUID().toString();
        try {
            JSONObject wrapper = new JSONObject();
            wrapper.put("messageId", messageId);
            wrapper.put("payload", payload);
            kafkaTemplate.send(CacheKeys.USER_ACTION_LOG_PERSIST_TOPIC, userKey, wrapper.toJSONString()).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("user action log persist send failed, userKey={}, messageId={}", userKey, messageId, e);
            throw new IllegalStateException("failed to send user action log persist message", e);
        }
    }
}

