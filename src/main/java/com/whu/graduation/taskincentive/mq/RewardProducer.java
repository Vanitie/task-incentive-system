package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.dto.Reward;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 发放奖励生产者
 */
@Slf4j
@Component
public class RewardProducer {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final String TOPIC = "reward-topic";

    public void sendReward(Long userId, Reward reward) {

        JSONObject msg = new JSONObject();

        msg.put("messageId", UUID.randomUUID().toString());
        msg.put("userId", userId);
        msg.put("reward", reward);

        kafkaTemplate.send(
                TOPIC,
                userId.toString(),
                msg.toJSONString()
        );
    }
}
