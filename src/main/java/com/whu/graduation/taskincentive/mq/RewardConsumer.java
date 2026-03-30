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

    private static final int GRANT_SUCCESS = 2;

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

        JSONObject json;
        try {
            json = JSON.parseObject(message);
        } catch (Exception e) {
            log.error("invalid reward json payload, raw={}", message, e);
            try {
                errorPublisher.publishToDlq("reward-topic", message, null, "invalid json payload",
                        Map.of("source", "RewardConsumer", "exceptionClass", e.getClass().getName(), "retryCount", 0));
            } catch (Exception ignored) {}
            return;
        }
        if (json == null) {
            log.error("empty reward json payload, raw={}", message);
            try {
                errorPublisher.publishToDlq("reward-topic", message, null, "empty json payload",
                        Map.of("source", "RewardConsumer", "retryCount", 0));
            } catch (Exception ignored) {}
            return;
        }

        String messageId = json.getString("messageId");
        String dedupKey = messageId == null ? null : CacheKeys.DEDUP_MSG_PREFIX + messageId;

        Long userId = json.getLong("userId");

        Reward reward = json.getObject("reward", Reward.class);

        if (userId == null || reward == null || reward.getRewardType() == null) {
            log.error("invalid reward message, missing required fields, messageId={}, payload={}", messageId, message);
            try {
                errorPublisher.publishToDlq("reward-topic", message, messageId, "invalid reward message",
                        Map.of("source", "RewardConsumer", "retryCount", 0));
            } catch (Exception ignored) {}
            return;
        }

        if (messageId != null) {
            UserRewardRecord existing = recordService.initRecordIfAbsent(messageId, userId, reward);
            if (existing != null && Integer.valueOf(GRANT_SUCCESS).equals(existing.getGrantStatus())) {
                log.info("duplicate reward message ignored by db idempotency messageId={}", messageId);
                return;
            }
            if (!recordService.markProcessing(messageId)) {
                UserRewardRecord latest = recordService.selectByMessageId(messageId);
                if (latest != null && Integer.valueOf(GRANT_SUCCESS).equals(latest.getGrantStatus())) {
                    log.info("reward message already completed, skip duplicate messageId={}", messageId);
                } else {
                    log.info("reward message currently processing or in terminal state, skip messageId={}", messageId);
                }
                return;
            }
        } else {
            log.warn("reward message without messageId, processing but risk duplication");
        }

        RewardStrategy strategy =
                rewardStrategies.get(reward.getRewardType());

        if (strategy == null) {
            log.error("未知奖励类型 {}", reward.getRewardType());
            if (messageId != null) {
                recordService.markFailedNewTx(messageId, "unknown reward type: " + reward.getRewardType());
            }
            return;
        }

        try {
            // 1 发放奖励
            strategy.grantReward(userId, reward);

            // 2 更新奖励日志状态（有 messageId 的消息采用状态机；无 messageId 走兼容旧逻辑）
            if (messageId != null) {
                if (!recordService.markSuccess(messageId)) {
                    log.warn("reward markSuccess skipped due to unexpected state, messageId={}", messageId);
                }
                try {
                    redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", dedupTtlDays, TimeUnit.DAYS);
                } catch (Exception ignore) {
                    log.warn("failed to set redis dedup key for reward messageId={}, err={}", messageId, ignore.getMessage());
                }
            } else {
                UserRewardRecord userRewardRecord = UserRewardRecord.builder()
                        .userId(userId)
                        .taskId(reward.getTaskId())
                        .rewardType(reward.getRewardType().toString())
                        .status(0)
                        .rewardValue(reward.getAmount())
                        .rewardId(reward.getRewardId())
                        .grantStatus(GRANT_SUCCESS)
                        .createTime(new Date())
                        .build();
                recordService.save(userRewardRecord);
            }
        } catch (Exception e) {
            if (messageId != null) {
                try {
                    recordService.markFailedNewTx(messageId, e.getMessage());
                } catch (Exception ignored) {
                    log.warn("mark reward failed status error, messageId={}", messageId);
                }
            }
            log.error("reward processing failed, sending to DLQ, messageId={}", messageId, e);
            try {
                errorPublisher.publishToDlq("reward-topic", message, messageId, e.getMessage(),
                        Map.of("source", "RewardConsumer", "exceptionClass", e.getClass().getName(), "retryCount", 1));
            } catch (Exception ignored) {}
            throw e;
        }
    }
}
