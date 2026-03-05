package com.whu.graduation.taskincentive.engine;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.common.enums.StockType;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.event.UserEvent;
import com.whu.graduation.taskincentive.service.RewardService;
import com.whu.graduation.taskincentive.strategy.task.TaskStrategy;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 任务规则引擎
 */
@Slf4j
@Component
public class TaskEngine {

    @Autowired
    private Map<String, TaskStrategy> strategies;

    @Autowired
    private UserTaskInstanceService instanceService;

    @Autowired
    private TaskConfigService taskConfigService;

    @Autowired
    private RewardService rewardService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final String TASK_TOPIC = "task-persist-topic";

    // 本地缓存：TaskConfig，key = taskId
    private final Cache<Long, TaskConfig> localTaskConfigCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    /**
     * 处理用户事件
     */
    public void processEvent(UserEvent event) {
        // 1. 从Redis获取事件对应的所有taskId
        Set<String> taskIdStrs = redisTemplate.opsForSet().members("event:" + event.getEventType());
        if (taskIdStrs == null || taskIdStrs.isEmpty()) return;

        for (String idStr : taskIdStrs) {
            Long taskId = Long.valueOf(idStr);

            // 2. 多级缓存读取TaskConfig
            TaskConfig taskConfig = getTaskConfig(taskId);

            // 3. 获取用户任务实例
            UserTaskInstance instance = getUserTaskInstance(event.getUserId(), taskId);

            // 4. 执行策略
            TaskStrategy strategy = strategies.get(taskConfig.getTaskType());
            if (strategy.execute(event, taskConfig, instance)) {
                // 1. 根据 totalStock 判断库存类型
                StockType stockType;
                if (taskConfig.getTotalStock() != null && taskConfig.getTotalStock() > 0) {
                    stockType = StockType.LIMITED;
                } else {
                    stockType = StockType.UNLIMITED;
                }

                // 2. 构建 Reward 对象
                Reward reward = Reward.builder()
                        .rewardId(IdWorker.getId())
                        .taskId(taskId)
                        .rewardType(RewardType.valueOf(taskConfig.getRewardType().toUpperCase()))
                        .amount(taskConfig.getRewardValue())
                        .stockType(stockType)
                        .build();

                // 3. 发放奖励
                rewardService.grantReward(event.getUserId(), reward);
            }

            // 5. 更新用户任务实例
            updateUserTaskInstance(instance);
        }
    }

    /**
     * 获取 TaskConfig，多级缓存
     * @param taskId 任务id
     * @return 任务详情
     */
    private TaskConfig getTaskConfig(Long taskId) {
        // 1. 本地缓存
        TaskConfig task = localTaskConfigCache.getIfPresent(taskId);
        if (task != null) return task;

        // 2. Redis
        String redisKey = "taskConfig:" + taskId;
        String json = redisTemplate.opsForValue().get(redisKey);
        if (json != null) {
            task = JSON.parseObject(json, TaskConfig.class);
            localTaskConfigCache.put(taskId, task);
            return task;
        }

        // 3. 数据库
        task = taskConfigService.getById(taskId);
        if (task != null) {
            redisTemplate.opsForValue().set(redisKey, JSON.toJSONString(task), 60, TimeUnit.SECONDS);
            localTaskConfigCache.put(taskId, task);
        }

        return task;
    }

    /**
     * 获取用户任务进度
     * @param userId 用户id
     * @param taskId 任务id
     * @return 用户任务进度
     */
    private UserTaskInstance getUserTaskInstance(Long userId, Long taskId) {
        String redisKey = "userTask:" + userId + ":" + taskId;
        String json = redisTemplate.opsForValue().get(redisKey);

        if (json != null) {
            return JSON.parseObject(json, UserTaskInstance.class);
        }

        UserTaskInstance instance = instanceService.getOrCreate(userId, taskId);
        redisTemplate.opsForValue().set(redisKey, JSON.toJSONString(instance));
        return instance;
    }

    /**
     * 更新用户任务进度
     * @param instance 用户任务进度
     */
    private void updateUserTaskInstance(UserTaskInstance instance) {
        String redisKey = buildUserTaskKey(instance.getUserId(), instance.getTaskId());
        redisTemplate.opsForValue().set(redisKey, JSON.toJSONString(instance));

        kafkaTemplate.send(
                TASK_TOPIC,
                //以userid作为key，使得同一个用户的消息都送入同一个partition，利用partition的顺序性保证消息顺序性
                instance.getUserId().toString(),
                JSON.toJSONString(instance)
        );
    }

    /**
     * 构造Redis Key
     */
    private String buildUserTaskKey(Long userId, Long taskId) {
        return "userTask:" + userId + ":" + taskId;
    }
}