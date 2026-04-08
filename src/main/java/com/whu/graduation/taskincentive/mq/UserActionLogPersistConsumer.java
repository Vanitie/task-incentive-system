package com.whu.graduation.taskincentive.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import com.whu.graduation.taskincentive.dao.entity.UserActionLog;
import com.whu.graduation.taskincentive.service.UserActionLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户行为日志落库消费者
 */
@Slf4j
@Component
public class UserActionLogPersistConsumer {

    private final UserActionLogService userActionLogService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ErrorPublisher errorPublisher;

    public UserActionLogPersistConsumer(UserActionLogService userActionLogService,
                                        RedisTemplate<String, String> redisTemplate,
                                        ErrorPublisher errorPublisher) {
        this.userActionLogService = userActionLogService;
        this.redisTemplate = redisTemplate;
        this.errorPublisher = errorPublisher;
    }

    @KafkaListener(topics = CacheKeys.USER_ACTION_LOG_PERSIST_TOPIC, groupId = "user-action-log-persist-group")
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
            log.debug("user action log persist wrapper parse failed: {}", ex.getMessage());
        }

        String dedupKey = null;
        if (messageId != null) {
            dedupKey = CacheKeys.DEDUP_MSG_PREFIX + messageId;
            try {
                Boolean exists = redisTemplate.hasKey(dedupKey);
                if (Boolean.TRUE.equals(exists)) {
                    acknowledgment.acknowledge();
                    return;
                }
            } catch (Exception e) {
                log.warn("user action log dedup check failed, messageId={}, err={}", messageId, e.getMessage());
            }
        }

        UserActionLogPersistMessage msg;
        try {
            msg = JSON.parseObject(payload, UserActionLogPersistMessage.class);
        } catch (Exception e) {
            try {
                errorPublisher.publishToDlq(CacheKeys.USER_ACTION_LOG_PERSIST_TOPIC, message, messageId, e.getMessage(),
                        Map.of("source", "UserActionLogPersistConsumer", "exceptionClass", e.getClass().getName(), "retryCount", 0));
            } catch (Exception ignored) {
            }
            acknowledgment.acknowledge();
            return;
        }

        try {
            UserActionLog actionLog = msg == null ? null : msg.getActionLog();
            if (actionLog != null) {
                userActionLogService.save(actionLog);
            }
            if (messageId != null) {
                try {
                    redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", CacheKeys.DEFAULT_DEDUP_TTL_DAYS, TimeUnit.DAYS);
                } catch (Exception e) {
                    log.warn("user action log set dedup key failed, messageId={}, err={}", messageId, e.getMessage());
                }
            }
            acknowledgment.acknowledge();
        } catch (DuplicateKeyException e) {
            acknowledgment.acknowledge();
        } catch (Exception e) {
            try {
                errorPublisher.publishToDlq(CacheKeys.USER_ACTION_LOG_PERSIST_TOPIC, message, messageId, e.getMessage(),
                        Map.of("source", "UserActionLogPersistConsumer", "exceptionClass", e.getClass().getName(), "retryCount", 1));
            } catch (Exception ignored) {
            }
            throw e;
        }
    }
}

