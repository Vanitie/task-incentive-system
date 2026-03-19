package com.whu.graduation.taskincentive.engine;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.common.enums.StockType;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.event.UserEvent;
import com.whu.graduation.taskincentive.service.RewardService;
import com.whu.graduation.taskincentive.strategy.stock.StockStrategy;
import com.whu.graduation.taskincentive.strategy.task.TaskStrategy;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.constant.CacheKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务规则引擎
 */
@Slf4j
@Component
public class TaskEngine {

    @Autowired
    private Map<String, TaskStrategy> taskStrategyMap; // Spring 会自动注入所有 TaskStrategy 实现类，key 为 @Component 的 value

    @Autowired
    private Map<String, StockStrategy> stockStrategyMap;

    @Autowired
    private UserTaskInstanceService instanceService;

    @Autowired
    private TaskConfigService taskConfigService;

    @Autowired
    private RewardService rewardService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 处理用户事件：TaskEngine 只负责编排，缓存/Redis/Kafka 交由相应的 Service 处理
     */
    public void processEvent(UserEvent event) {
        String eventKey = CacheKeys.EVENT_TASKS_PREFIX + event.getEventType();
        String userKey = "user:accepted:" + event.getUserId();

        Set<String> intersect;
        try {
            intersect = redisTemplate.opsForSet().intersect(eventKey, userKey);
        } catch (Exception e) {
            log.debug("redis SINTER failed for eventKey={}, userKey={}, err={}", eventKey, userKey, e.getMessage());
            intersect = null;
        }

        Set<Long> eventTaskIds;

        if (intersect != null && !intersect.isEmpty()) {
            // 1. 将 Redis 交集结果解析为 Long 集合
            eventTaskIds = intersect.stream().map(s -> {
                try { return Long.valueOf(s); } catch (Exception ex) { return null; }
            }).filter(Objects::nonNull).collect(Collectors.toSet());
            if (eventTaskIds.isEmpty()) return;

            // 2. 批量获取这些任务的配置（降低多次查库/缓存的开销）
            Map<Long, TaskConfig> configs = taskConfigService.getTaskConfigsByIds(eventTaskIds);

            // 3. 一次性获取该用户的实例列表并构建 taskId -> instance 的映射，便于 O(1) 查找
            List<UserTaskInstance> userInstances = instanceService.selectByUserId(event.getUserId());
            Map<Long, UserTaskInstance> instanceByTaskId = userInstances == null ? java.util.Collections.emptyMap()
                    : userInstances.stream().filter(Objects::nonNull).filter(i -> i.getStatus() != null && i.getStatus() > 0 && i.getTaskId() != null)
                    .collect(Collectors.toMap(UserTaskInstance::getTaskId, i -> i));

            processMatchedTasks(event, eventTaskIds, configs, instanceByTaskId);

            // 4. 已通过 Redis 交集处理完毕，直接返回
            return;
        }

        // 1. 回退：若 Redis 交集不可用，则在应用端计算交集并处理（兼容性保障）
        Set<String> taskIdStrs = taskConfigService.getTaskIdsByEventType(event.getEventType());
        if (taskIdStrs == null || taskIdStrs.isEmpty()) return;

        List<UserTaskInstance> userInstances = instanceService.selectByUserId(event.getUserId());
        if (userInstances == null || userInstances.isEmpty()) return;

        // 2. 从用户实例构造已接取任务 ID 集合
        Set<Long> acceptedTaskIds = userInstances.stream()
                .filter(Objects::nonNull)
                .filter(i -> i.getStatus() != null && i.getStatus() > 0)
                .map(UserTaskInstance::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (acceptedTaskIds.isEmpty()) return;

        // 3. 将事件关联的 taskId 字符串解析为 Long 并与用户已接取任务取交集
        Set<Long> parsedEventTaskIds = taskIdStrs.stream().map(s -> {
            try { return Long.valueOf(s); } catch (Exception e) { return null; }
        }).filter(Objects::nonNull).collect(Collectors.toSet());

        parsedEventTaskIds.retainAll(acceptedTaskIds);
        if (parsedEventTaskIds.isEmpty()) return;

        Map<Long, TaskConfig> configs = taskConfigService.getTaskConfigsByIds(parsedEventTaskIds);
        Map<Long, UserTaskInstance> instanceByTaskId = userInstances.stream().filter(Objects::nonNull).filter(i -> i.getStatus() != null && i.getStatus() > 0 && i.getTaskId() != null)
                .collect(Collectors.toMap(UserTaskInstance::getTaskId, i -> i));

        processMatchedTasks(event, parsedEventTaskIds, configs, instanceByTaskId);
    }

    /**
     * 公共处理逻辑：遍历任务，执行策略、库存、奖励、更新实例
     */
    private void processMatchedTasks(UserEvent event, Set<Long> taskIds, Map<Long, TaskConfig> configs, Map<Long, UserTaskInstance> instanceByTaskId) {
        for (Long taskId : taskIds) {
            TaskConfig taskConfig = configs.get(taskId);
            if (taskConfig == null) {
                log.warn("taskConfig not found, taskId={}", taskId);
                continue;
            }
            TaskStrategy strategy = taskStrategyMap.get(taskConfig.getTaskType());
            if (strategy == null) {
                log.warn("no strategy for taskType={}, taskId={}", taskConfig.getTaskType(), taskId);
                continue;
            }
            UserTaskInstance instance = instanceByTaskId.get(taskId);
            if (instance == null) {
                log.debug("user {} accepted task {} but instance not found, skip", event.getUserId(), taskId);
                continue;
            }
            // 执行策略，判断是否满足完成条件（由 TaskStrategy 内部维护进度和状态更新）
            if (strategy.execute(event, taskConfig, instance)) {
                // 任务完成，发放奖励前先尝试获取库存（如果有限量库存的话）
                StockStrategy stockStrategy = stockStrategyMap.get(taskConfig.getStockType());
                if (stockStrategy != null) {
                    boolean stockOk = stockStrategy.acquireStock(taskId);
                    if (!stockOk) {
                        log.info("stock not sufficient for taskId={}, userId={}, skip reward", taskId, event.getUserId());
                        continue;
                    }
                    Reward reward = Reward.builder()
                            .rewardId(IdWorker.getId())
                            .taskId(taskId)
                            .rewardType(RewardType.valueOf(taskConfig.getRewardType().toUpperCase()))
                            .amount(taskConfig.getRewardValue())
                            .stockType(StockType.valueOf(taskConfig.getStockType().toUpperCase()))
                            .build();
                    rewardService.grantReward(event.getUserId(), reward);
                } else {
                    log.warn("no stock strategy for stockType={}, taskId={}", taskConfig.getStockType(), taskId);
                }
            }
            instanceService.updateAndPublish(instance);
        }
    }
}
