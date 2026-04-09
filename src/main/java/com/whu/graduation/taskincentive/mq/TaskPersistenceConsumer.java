package com.whu.graduation.taskincentive.mq;

import com.whu.graduation.taskincentive.constant.CacheKeys;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.Acknowledgment;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * 任务进度落库消费者
 */
@Slf4j
@Component
public class TaskPersistenceConsumer {

    @Autowired
    private UserTaskInstanceService instanceService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ErrorPublisher errorPublisher;

    @KafkaListener(topics = "task-persist-topic", groupId = "task-persist-group")
    public void consume(String message, Acknowledgment acknowledgment) {
        // 上游可能发送原始的 UserTaskInstance JSON，或发送封装格式如 {messageId:..., payload: {...}}
        String messageId = null;
        String payload = message;

        // 尝试识别封装并提取 messageId 与真实 payload
        try {
            JSONObject obj = JSON.parseObject(message);
            if (obj != null) {
                if (obj.containsKey("messageId")) {
                    messageId = obj.getString("messageId");
                }
                // 常见的封装字段：payload / data / body / instance
                if (obj.containsKey("payload")) {
                    Object p = obj.get("payload");
                    payload = p instanceof String ? (String) p : JSONObject.toJSONString(p);
                } else if (obj.containsKey("data")) {
                    Object p = obj.get("data");
                    payload = p instanceof String ? (String) p : JSONObject.toJSONString(p);
                } else if (obj.containsKey("body")) {
                    Object p = obj.get("body");
                    payload = p instanceof String ? (String) p : JSONObject.toJSONString(p);
                } else if (obj.containsKey("instance")) {
                    Object p = obj.get("instance");
                    payload = p instanceof String ? (String) p : JSONObject.toJSONString(p);
                }
            }
        } catch (Exception ex) {
            // 不是 JSON 或解析封装失败，则将完整 message 视为 payload 处理
            log.debug("message not a wrapper JSON or failed to parse wrapper, treating raw as payload: {}", ex.getMessage());
        }

        String dedupKey = null;
        long dedupTtlDays = CacheKeys.DEFAULT_DEDUP_TTL_DAYS;
        // 在处理前检查去重标记是否已存在
        if (messageId != null) {
            dedupKey = CacheKeys.DEDUP_MSG_PREFIX + messageId;
            try {
                Boolean exists = redisTemplate.hasKey(dedupKey);
                if (Boolean.TRUE.equals(exists)) {
                    log.info("duplicate task-persist message ignored messageId={}", messageId);
                    acknowledgment.acknowledge();
                    return;
                }
            } catch (Exception e) {
                // Redis 不可用或发生错误：记录并继续处理以避免数据丢失
                log.warn("failed to check dedup key in redis, will proceed without dedup check, messageId={}, err={}", messageId, e.getMessage());
            }
        }

        UserTaskInstance instance;
        try {
            instance = JSON.parseObject(payload, UserTaskInstance.class);
        } catch (Exception e) {
            log.error("failed to parse payload into UserTaskInstance, payload={}", payload, e);
            // 解析失败 => 无法处理此消息；发送到 DLQ 并 ack，避免成为毒丸消息
            try {
                errorPublisher.publishToDlq("task-persist-topic", message, messageId, e.getMessage(),
                        Map.of("source", "TaskPersistenceConsumer", "exceptionClass", e.getClass().getName(), "retryCount", 0));
            } catch (Exception ignored) {}
            acknowledgment.acknowledge();
            return;
        }

        if (instance == null || instance.getId() == null) {
            log.warn("missing instance id, send to DLQ, messageId={}, payload={}", messageId, payload);
            try {
                errorPublisher.publishToDlq("task-persist-topic", message, messageId, "missing instance id",
                        Map.of("source", "TaskPersistenceConsumer", "retryCount", 0));
            } catch (Exception ignored) {}
            acknowledgment.acknowledge();
            return;
        }

        try {
            int rows = instanceService.updateWithVersion(instance);
            if (rows == 0) {
                UserTaskInstance latest = instanceService.getById(instance.getId());
                if (latest != null) {
                    instance.setVersion(latest.getVersion());
                    instanceService.updateWithVersion(instance);
                } else {
                    // 如果不存在，可能需要根据业务决定是否插入
                    log.warn("instance with id={} not found when updateWithVersion returned 0", instance.getId());
                }
            }

            // 处理成功后，若存在 messageId 则持久化去重标记
            if (messageId != null) {
                try {
                    Boolean set = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", dedupTtlDays, TimeUnit.DAYS);
                    if (Boolean.TRUE.equals(set)) {
                        log.debug("set dedup key for messageId={} with ttl {} days", messageId, dedupTtlDays);
                    }
                } catch (Exception e) {
                    // 若设置失败，记录日志但不影响业务继续（后续可能导致重复处理）
                    log.warn("failed to set dedup key for messageId={}, err={}", messageId, e.getMessage());
                }
            }

            acknowledgment.acknowledge(); // 手动提交 offset
        } catch (Exception e) {
            log.error("持久化失败 for payload={}, messageId={}", payload, messageId, e);
            // 处理失败：发送到 DLQ 并抛出异常，让 Kafka 重试或根据策略处理
            try {
                errorPublisher.publishToDlq("task-persist-topic", message, messageId, e.getMessage(),
                        Map.of("source", "TaskPersistenceConsumer", "exceptionClass", e.getClass().getName(), "retryCount", 1));
            } catch (Exception ignored) {}
            throw e; // 不 ack -> Kafka 将重试
        }
    }
}
