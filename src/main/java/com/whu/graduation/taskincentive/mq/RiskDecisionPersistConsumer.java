package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import com.whu.graduation.taskincentive.dao.entity.RewardFreezeRecord;
import com.whu.graduation.taskincentive.dao.mapper.RiskDecisionLogMapper;
import com.whu.graduation.taskincentive.dao.mapper.RewardFreezeRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * 风控决策落库消费者
 */
@Slf4j
@Component
public class RiskDecisionPersistConsumer {

    private final RiskDecisionLogMapper decisionLogMapper;
    private final RewardFreezeRecordMapper rewardFreezeRecordMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final ErrorPublisher errorPublisher;

    public RiskDecisionPersistConsumer(RiskDecisionLogMapper decisionLogMapper,
                                       RewardFreezeRecordMapper rewardFreezeRecordMapper,
                                       RedisTemplate<String, String> redisTemplate,
                                       ErrorPublisher errorPublisher) {
        this.decisionLogMapper = decisionLogMapper;
        this.rewardFreezeRecordMapper = rewardFreezeRecordMapper;
        this.redisTemplate = redisTemplate;
        this.errorPublisher = errorPublisher;
    }

    @KafkaListener(topics = CacheKeys.RISK_DECISION_PERSIST_TOPIC, groupId = "risk-decision-persist-group")
    public void consume(String message, Acknowledgment acknowledgment) {
        String messageId = null;
        String payload = message;

        try {
            JSONObject obj = JSON.parseObject(message);
            if (obj != null) {
                if (obj.containsKey("messageId")) {
                    messageId = obj.getString("messageId");
                }
                if (obj.containsKey("payload")) {
                    Object p = obj.get("payload");
                    payload = p instanceof String ? (String) p : JSONObject.toJSONString(p);
                }
            }
        } catch (Exception ex) {
            log.debug("risk decision persist message wrapper parse failed: {}", ex.getMessage());
        }

        String dedupKey = null;
        if (messageId != null) {
            dedupKey = CacheKeys.DEDUP_MSG_PREFIX + messageId;
            try {
                Boolean exists = redisTemplate.hasKey(dedupKey);
                if (Boolean.TRUE.equals(exists)) {
                    log.info("duplicate risk decision persist message ignored messageId={}", messageId);
                    acknowledgment.acknowledge();
                    return;
                }
            } catch (Exception e) {
                log.warn("dedup check failed, messageId={}, err={}", messageId, e.getMessage());
            }
        }

        RiskDecisionPersistMessage msg;
        try {
            msg = JSON.parseObject(payload, RiskDecisionPersistMessage.class);
        } catch (Exception e) {
            log.error("failed to parse risk decision persist payload, payload={}", payload, e);
            try {
                errorPublisher.publishToDlq(CacheKeys.RISK_DECISION_PERSIST_TOPIC, message, messageId, e.getMessage(),
                        Map.of("source", "RiskDecisionPersistConsumer", "exceptionClass", e.getClass().getName(), "retryCount", 0));
            } catch (Exception ignored) {}
            acknowledgment.acknowledge();
            return;
        }

        try {
            RiskDecisionLog logEntity = msg == null ? null : msg.getDecisionLog();
            if (logEntity != null) {
                decisionLogMapper.insert(logEntity);
            }
            RewardFreezeRecord freeze = msg == null ? null : msg.getFreezeRecord();
            if (freeze != null) {
                rewardFreezeRecordMapper.insert(freeze);
            }

            if (messageId != null) {
                try {
                    redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", CacheKeys.DEFAULT_DEDUP_TTL_DAYS, TimeUnit.DAYS);
                } catch (Exception e) {
                    log.warn("failed to set dedup key for messageId={}, err={}", messageId, e.getMessage());
                }
            }

            acknowledgment.acknowledge();
        } catch (DuplicateKeyException e) {
            // 同一 requestId 的日志已存在，视为幂等成功，避免无意义重试
            log.info("risk decision persist duplicated and ignored, payload={}, messageId={}", payload, messageId);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("risk decision persist failed, payload={}, messageId={}", payload, messageId, e);
            try {
                errorPublisher.publishToDlq(CacheKeys.RISK_DECISION_PERSIST_TOPIC, message, messageId, e.getMessage(),
                        Map.of("source", "RiskDecisionPersistConsumer", "exceptionClass", e.getClass().getName(), "retryCount", 1));
            } catch (Exception ignored) {}
            throw e;
        }
    }
}
