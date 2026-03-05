package com.whu.graduation.taskincentive.engine;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

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

    /**
     * 处理用户事件：TaskEngine 只负责编排，缓存/Redis/Kafka 交由相应 Service 处理
     */
    public void processEvent(UserEvent event) {
        // 1. 从事件->任务映射（由 TaskConfigService 或外部组件维护，这里通过 taskConfigService 查询任务ID集合的实现留在 service 层）
        // 为最小改动，继续从 TaskConfigService 获取关联的 taskIds by event type
        Set<String> taskIdStrs = taskConfigService.getTaskIdsByEventType(event.getEventType());
        if (taskIdStrs == null || taskIdStrs.isEmpty()) return;

        for (String idStr : taskIdStrs) {
            Long taskId = Long.valueOf(idStr);

            // 2. 从 service 获取 TaskConfig（service 内负责多级缓存）
            TaskConfig taskConfig = taskConfigService.getTaskConfig(taskId);
            if (taskConfig == null) {
                log.warn("taskConfig not found, taskId={}", taskId);
                continue;
            }

            // 3. 仅获取用户已接取的实例；不自动创建
            UserTaskInstance instance = instanceService.getAcceptedInstance(event.getUserId(), taskId);
            if (instance == null) {
                // 用户未接取该任务，跳过
                log.debug("user {} has not accepted task {}, skip", event.getUserId(), taskId);
                continue;
            }

            // 4. 执行策略（策略负责修改 instance 的进度/状态）
            TaskStrategy strategy = strategies.get(taskConfig.getTaskType());
            if (strategy == null) {
                log.warn("no strategy for taskType={}, taskId={}", taskConfig.getTaskType(), taskId);
                continue;
            }

            if (strategy.execute(event, taskConfig, instance)) {
                StockType stockType = (taskConfig.getTotalStock() != null && taskConfig.getTotalStock() > 0)
                        ? StockType.LIMITED : StockType.UNLIMITED;

                Reward reward = Reward.builder()
                        .rewardId(IdWorker.getId())
                        .taskId(taskId)
                        .rewardType(RewardType.valueOf(taskConfig.getRewardType().toUpperCase()))
                        .amount(taskConfig.getRewardValue())
                        .stockType(stockType)
                        .build();

                rewardService.grantReward(event.getUserId(), reward);
            }

            // 5. 更新并发布用户任务实例（由 service 负责缓存更新与 Kafka 发布）
            instanceService.updateAndPublish(instance);
        }
    }
}