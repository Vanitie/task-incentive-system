package com.whu.graduation.taskincentive.engine;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.common.enums.RewardType;
import com.whu.graduation.taskincentive.common.enums.StockType;
import com.whu.graduation.taskincentive.common.enums.UserTaskStatus;
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
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionRequest;
import com.whu.graduation.taskincentive.dto.risk.RiskDecisionResponse;
import com.whu.graduation.taskincentive.service.risk.RiskDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Date;
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
    private RiskDecisionService riskDecisionService;

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
            if (!isTaskActive(taskConfig, event.getTime())) {
                log.debug("task not active, taskId={}, userId={}", taskId, event.getUserId());
                continue;
            }
            TaskStrategy strategy = taskStrategyMap.get(taskConfig.getTaskType());
            if (strategy == null) {
                log.warn("no strategy for taskType={}, taskId={}", taskConfig.getTaskType(), taskId);
                continue;
            }
            UserTaskInstance instance = instanceByTaskId.get(taskId);
            if (!isInstanceValid(instance, event.getUserId(), taskId)) {
                log.debug("invalid user task instance, userId={}, taskId={}", event.getUserId(), taskId);
                continue;
            }
            // 执行策略，返回本次事件触发的奖励阶梯序号列表
            List<Integer> rewardStages = strategy.execute(event, taskConfig, instance);
            if (rewardStages != null && !rewardStages.isEmpty()) {
                // 1. 风控评估（达标后、扣库存前）
                RiskDecisionRequest riskReq = RiskDecisionRequest.builder()
                        .requestId(event.getRequestId())
                        .eventId(event.getEventId())
                        .userId(event.getUserId())
                        .taskId(taskId)
                        .eventType(event.getEventType())
                        .eventTime(event.getTime())
                        .amount(taskConfig.getRewardValue())
                        .deviceId(event.getDeviceId())
                        .ip(event.getIp())
                        .channel(event.getChannel())
                        .ext(event.getExt())
                        .build();
                RiskDecisionResponse riskResp = riskDecisionService.evaluate(riskReq);
                if (riskResp == null || riskResp.getDecision() == null) {
                    log.warn("risk decision empty, taskId={}, userId={}, skip", taskId, event.getUserId());
                    continue;
                }
                String decision = riskResp.getDecision();
                if ("REJECT".equalsIgnoreCase(decision) || "REVIEW".equalsIgnoreCase(decision)
                        || "FREEZE".equalsIgnoreCase(decision)) {
                    log.info("risk blocked, decision={}, taskId={}, userId={}", decision, taskId, event.getUserId());
                    continue;
                }

                for (Integer stage : rewardStages) {
                    // 2. 阶梯任务：每个阶段单独扣减库存
                    StockStrategy stockStrategy = stockStrategyMap.get(taskConfig.getStockType());
                    boolean stockOk = true;
                    if (stockStrategy != null) {
                        stockOk = stockStrategy.acquireStock(taskId, stage);
                        if (!stockOk) {
                            log.info("stock not sufficient for taskId={}, stage={}, userId={}, skip reward", taskId, stage, event.getUserId());
                            continue;
                        }
                    }

                    // 3. 构建奖励
                    Integer rewardAmount = taskConfig.getRewardValue();
                    if ("DEGRADE_PASS".equalsIgnoreCase(decision)) {
                        double ratio = riskResp.getDegradeRatio() == null ? 0.5 : riskResp.getDegradeRatio();
                        rewardAmount = Math.max(1, (int) Math.floor(rewardAmount * ratio));
                    }

                    Reward reward = Reward.builder()
                            .rewardId(IdWorker.getId())
                            .taskId(taskId)
                            .rewardType(RewardType.valueOf(taskConfig.getRewardType().toUpperCase()))
                            .amount(rewardAmount)
                            .stockType(StockType.valueOf(taskConfig.getStockType().toUpperCase()))
                            .stageIndex(stage)
                            .build();
                    rewardService.grantReward(event.getUserId(), reward);
                }
            }
            instanceService.updateAndPublish(instance);
        }
    }

    private boolean isTaskActive(TaskConfig taskConfig, java.time.LocalDateTime eventTime) {
        if (taskConfig.getStatus() == null || taskConfig.getStatus() != 1) {
            return false;
        }
        Date now = eventTime == null
                ? new Date()
                : Date.from(eventTime.atZone(ZoneId.systemDefault()).toInstant());
        if (taskConfig.getStartTime() != null && now.before(taskConfig.getStartTime())) {
            return false;
        }
        if (taskConfig.getEndTime() != null && now.after(taskConfig.getEndTime())) {
            return false;
        }
        return true;
    }

    private boolean isInstanceValid(UserTaskInstance instance, Long userId, Long taskId) {
        if (instance == null) return false;
        if (instance.getUserId() == null || !instance.getUserId().equals(userId)) return false;
        if (instance.getTaskId() == null || !instance.getTaskId().equals(taskId)) return false;
        UserTaskStatus status = UserTaskStatus.fromCode(instance.getStatus());
        if (status == null) return false;
        return status != UserTaskStatus.COMPLETED && status != UserTaskStatus.CANCELLED;
    }
}
