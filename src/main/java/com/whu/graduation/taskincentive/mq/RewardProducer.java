package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dto.Reward;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        String messageId = UUID.randomUUID().toString();
        sendRewardWithMessageId(userId, reward, messageId);
    }

    public void sendRewardWithMessageId(Long userId, Reward reward, String messageId) {

        if (reward != null && reward.getRewardId() == null) {
            reward.setRewardId(IdWorker.getId());
        }

        JSONObject msg = new JSONObject();

        msg.put("messageId", messageId);
        msg.put("userId", userId);
        msg.put("reward", reward);

        try {
            kafkaTemplate.send(
                    TOPIC,
                    userId.toString(),
                    msg.toJSONString()
            ).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("reward message send failed messageId={}, userId={}, rewardId={}", messageId, userId, reward == null ? null : reward.getRewardId(), e);
            throw new IllegalStateException("failed to send reward message", e);
        }

        log.info("reward message produced messageId={}, userId={}, rewardId={}", messageId, userId, reward == null ? null : reward.getRewardId());
    }
}
