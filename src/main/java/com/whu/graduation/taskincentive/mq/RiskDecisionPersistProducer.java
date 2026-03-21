package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 风控决策落库生产者
 */
@Slf4j
@Component
public class RiskDecisionPersistProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public RiskDecisionPersistProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String userKey, Object payload) {
        try {
            JSONObject wrapper = new JSONObject();
            wrapper.put("messageId", UUID.randomUUID().toString());
            wrapper.put("payload", payload);
            kafkaTemplate.send(CacheKeys.RISK_DECISION_PERSIST_TOPIC, userKey, wrapper.toJSONString());
        } catch (Exception e) {
            log.warn("risk decision persist send failed, userKey={}, err={}", userKey, e.getMessage());
        }
    }
}
