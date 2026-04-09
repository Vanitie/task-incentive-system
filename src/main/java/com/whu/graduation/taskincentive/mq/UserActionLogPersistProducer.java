package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import com.whu.graduation.taskincentive.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 用户行为日志落库生产者
 */
@Slf4j
@Component
public class UserActionLogPersistProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ErrorPublisher errorPublisher;
    private final AppProperties appProperties;

    public UserActionLogPersistProducer(KafkaTemplate<String, String> kafkaTemplate,
                                        ErrorPublisher errorPublisher,
                                        AppProperties appProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.errorPublisher = errorPublisher;
        this.appProperties = appProperties;
    }

    public void send(String userKey, UserActionLogPersistMessage payload) {
        String messageId = UUID.randomUUID().toString();
        JSONObject wrapper = new JSONObject();
        wrapper.put("messageId", messageId);
        wrapper.put("payload", payload);
        String message = wrapper.toJSONString();
        kafkaTemplate.send(CacheKeys.USER_ACTION_LOG_PERSIST_TOPIC, userKey, message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        return;
                    }
                    log.error("user action log persist async send failed, userKey={}, messageId={}", userKey, messageId, ex);
                    if (appProperties.getAsyncCompensation() != null
                            && appProperties.getAsyncCompensation().isDlqOnKafkaFailure()) {
                        errorPublisher.publishToDlq(
                                CacheKeys.USER_ACTION_LOG_PERSIST_TOPIC,
                                message,
                                messageId,
                                ex.getMessage(),
                                Map.of("source", "UserActionLogPersistProducer", "userKey", userKey));
                    }
                });
    }
}

