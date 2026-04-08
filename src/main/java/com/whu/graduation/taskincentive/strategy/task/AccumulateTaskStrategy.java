package com.whu.graduation.taskincentive.strategy.task;

import com.alibaba.fastjson.JSON;
import com.whu.graduation.taskincentive.common.enums.UserTaskStatus;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dto.AccumulateRuleConfig;
import com.whu.graduation.taskincentive.event.UserEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 累积任务策略
 */
@Slf4j
@Component("ACCUMULATE")
public class AccumulateTaskStrategy implements TaskStrategy {

    @Override
    public List<Integer> execute(UserEvent event, TaskConfig taskConfig, UserTaskInstance instance) {
        // 累加进度
        int current = instance.getProgress() + event.getValue();
        instance.setProgress(current);

        AccumulateRuleConfig rule = null;
        try {
            rule = JSON.parseObject(taskConfig.getRuleConfig(), AccumulateRuleConfig.class);
        } catch (Exception e) {
            log.warn("accumulate rule parse failed, taskId={}, err={}", taskConfig.getId(), e.getMessage());
        }
        int target = rule == null || rule.getTargetValue() == null ? Integer.MAX_VALUE : rule.getTargetValue();

        if (current >= target) {
            instance.setStatus(UserTaskStatus.COMPLETED.getCode());
            return List.of(1);
        }

        return new ArrayList<>();
    }
}