package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
import com.whu.graduation.taskincentive.strategy.reward.RewardStrategy;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

/**
 * 发放奖励消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RewardConsumer {

    private final List<RewardStrategy> rewardStrategyList;

    private final UserRewardRecordService recordService;

    private Map<RewardType, RewardStrategy> rewardStrategies;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ErrorPublisher errorPublisher;

    private long dedupTtlDays = CacheKeys.DEFAULT_DEDUP_TTL_DAYS;

    @PostConstruct
    public void init() {
        rewardStrategies = rewardStrategyList.stream()
                .collect(Collectors.toMap(
                        RewardStrategy::getType,
                        Function.identity()
                ));
    }

    @KafkaListener(topics = "reward-topic", groupId = "reward-group")
    @Transactional
    public void consume(String message) {

        JSONObject json = JSON.parseObject(message);

        String messageId = json.getString("messageId");
        if (messageId == null) {
            log.warn("reward message without messageId, processing but risk duplication");
        } else {
            String dedupKey = CacheKeys.DEDUP_MSG_PREFIX + messageId;
            try {
                Boolean exists = redisTemplate.hasKey(dedupKey);
                if (Boolean.TRUE.equals(exists)) {
                    log.info("duplicate reward message ignored messageId={}", messageId);
                    return;
                }
                // mark processed with TTL
                redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", dedupTtlDays, TimeUnit.DAYS);
            } catch (Exception ignore) {
                log.warn("failed to operate redis dedup for reward messageId={}, err={}", messageId, ignore.getMessage());
            }
        }

        Long userId = json.getLong("userId");

        Reward reward = json.getObject("reward", Reward.class);

        RewardStrategy strategy =
                rewardStrategies.get(reward.getRewardType());

        if (strategy == null) {
            log.error("未知奖励类型 {}", reward.getRewardType());
            return;
        }

        try {
            // 1 发放奖励
            strategy.grantReward(userId, reward);

            UserRewardRecord userRewardRecord = UserRewardRecord.builder()
                    .userId(userId)
                    .taskId(reward.getTaskId())
                    .rewardType(reward.getRewardType().toString())
                    .status(0)
                    .rewardValue(reward.getAmount())
                    .createTime(new Date())
                    .build();

            // 2 写奖励日志
            recordService.save(userRewardRecord);
        } catch (Exception e) {
            log.error("reward processing failed, sending to DLQ, messageId={}", messageId, e);
            try { errorPublisher.publishToDlq("reward-topic", message, messageId, e.getMessage()); } catch (Exception ignored) {}
            throw e;
        }
    }
}
