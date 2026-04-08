package com.whu.graduation.taskincentive.strategy.task;

import com.alibaba.fastjson.JSON;
import com.whu.graduation.taskincentive.common.enums.UserTaskStatus;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dto.ContinuousExtraData;
import com.whu.graduation.taskincentive.dto.ContinuousRuleConfig;
import com.whu.graduation.taskincentive.event.UserEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 连续签到任务策略
 */
@Slf4j
@Component("CONTINUOUS")
public class ContinuousTaskStrategy implements TaskStrategy {

    @Override
    public List<Integer> execute(UserEvent event, TaskConfig taskConfig, UserTaskInstance instance) {
        ContinuousExtraData extra = null;
        if (instance.getExtraData() != null && !instance.getExtraData().isEmpty()) {
            try {
                extra = JSON.parseObject(instance.getExtraData(), ContinuousExtraData.class);
            } catch (Exception e) {
                log.debug("continuous extra parse failed, taskId={}, err={}", taskConfig.getId(), e.getMessage());
            }
        }
        if (extra == null) {
            extra = new ContinuousExtraData();
            extra.setContinuousDays(0);
        }

        LocalDate lastDate = extra.getLastSignDate();
        int continuous = extra.getContinuousDays() == null ? 0 : extra.getContinuousDays();

        LocalDate today = event.getTime().toLocalDate();

        if (lastDate != null && lastDate.plusDays(1).equals(today)) {
            continuous++;
        } else {
            continuous = 1;
        }

        extra.setLastSignDate(today);
        extra.setContinuousDays(continuous);
        instance.setExtraData(JSON.toJSONString(extra));

        ContinuousRuleConfig rule = null;
        try {
            rule = JSON.parseObject(taskConfig.getRuleConfig(), ContinuousRuleConfig.class);
        } catch (Exception e) {
            log.warn("continuous rule parse failed, taskId={}, err={}", taskConfig.getId(), e.getMessage());
        }
        int targetDays = rule == null || rule.getTargetDays() == null ? Integer.MAX_VALUE : rule.getTargetDays();

        if (continuous >= targetDays) {
            instance.setStatus(UserTaskStatus.COMPLETED.getCode());
            return List.of(1);
        }

        return new ArrayList<>();
    }
}
