package com.whu.graduation.taskincentive.engine.strategy;

import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.event.UserEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 累积任务策略
 */
@Slf4j
@Component("ACCUMULATE")
public class AccumulateTaskStrategy implements TaskStrategy {

    @Override
    public boolean execute(UserEvent event, TaskConfig taskConfig, UserTaskInstance instance) {
        // 累加进度
        int current = instance.getProgress() + event.getValue();
        instance.setProgress(current);

        // 从 ruleConfig JSON 中读取 targetValue
        String ruleConfig = taskConfig.getRuleConfig();
        JSONObject ruleJson = com.alibaba.fastjson.JSON.parseObject(ruleConfig);
        int target = ruleJson.getIntValue("targetValue");

        if (current >= target) {
            instance.setStatus(1); // 完成
            return true;
        }

        return false;
    }
}