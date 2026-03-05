package com.whu.graduation.taskincentive.mq;

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

    private static final String DEDUP_KEY = "mq:task-persist:processed";

    @KafkaListener(topics = "task-persist-topic", groupId = "task-persist-group")
    public void consume(String message, Acknowledgment acknowledgment) {
        // expect message is either raw UserTaskInstance JSON or wrapper with messageId
        String messageId = null;
        try {
            JSONObject obj = JSON.parseObject(message);
            messageId = obj.getString("messageId");
        } catch (Exception ignore) {}

        if (messageId != null) {
            Boolean exists = redisTemplate.opsForSet().isMember(DEDUP_KEY, messageId);
            if (Boolean.TRUE.equals(exists)) {
                log.info("duplicate task-persist message ignored messageId={}", messageId);
                acknowledgment.acknowledge();
                return;
            }
            try { redisTemplate.opsForSet().add(DEDUP_KEY, messageId); redisTemplate.expire(DEDUP_KEY, 7, TimeUnit.DAYS); } catch (Exception ignore){}
        }

        UserTaskInstance instance = JSON.parseObject(message, UserTaskInstance.class);

        try {
            int rows = instanceService.updateWithVersion(instance);
            if (rows == 0) {
                UserTaskInstance latest =
                        instanceService.getById(instance.getId());
                instance.setVersion(latest.getVersion());
                instanceService.updateWithVersion(instance);
            }
            acknowledgment.acknowledge(); // 手动提交offset
        } catch (Exception e) {
            log.error("持久化失败", e);
            throw e; // 不ack → Kafka自动重试
        }
    }
}
