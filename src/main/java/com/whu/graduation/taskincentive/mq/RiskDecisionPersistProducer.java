package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.config.AppProperties;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 风控决策落库生产者
 */
@Slf4j
@Component
public class RiskDecisionPersistProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ErrorPublisher errorPublisher;
    private final AppProperties appProperties;

    public RiskDecisionPersistProducer(KafkaTemplate<String, String> kafkaTemplate,
                                       ErrorPublisher errorPublisher,
                                       AppProperties appProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.errorPublisher = errorPublisher;
        this.appProperties = appProperties;
    }

    public void send(String userKey, Object payload) {
        String messageId = UUID.randomUUID().toString();
        JSONObject wrapper = new JSONObject();
        wrapper.put("messageId", messageId);
        wrapper.put("payload", payload);
        String message = wrapper.toJSONString();
        kafkaTemplate.send(CacheKeys.RISK_DECISION_PERSIST_TOPIC, userKey, message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        return;
                    }
                    log.error("risk decision persist async send failed, userKey={}, messageId={}", userKey, messageId, ex);
                    if (appProperties.getAsyncCompensation() != null
                            && appProperties.getAsyncCompensation().isDlqOnKafkaFailure()) {
                        errorPublisher.publishToDlq(
                                CacheKeys.RISK_DECISION_PERSIST_TOPIC,
                                message,
                                messageId,
                                ex.getMessage(),
                                Map.of("source", "RiskDecisionPersistProducer", "userKey", userKey));
                    }
                });
    }
}
