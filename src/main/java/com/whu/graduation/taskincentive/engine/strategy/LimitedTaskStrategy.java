package com.whu.graduation.taskincentive.engine.strategy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.event.UserEvent;
import com.whu.graduation.taskincentive.service.TaskStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 限量任务策略
 */
@Slf4j
@Component("LIMITED")
public class LimitedTaskStrategy implements TaskStrategy {

    @Autowired
    private TaskStockService taskStockService;

    @Override
    public boolean execute(UserEvent event, TaskConfig taskConfig, UserTaskInstance instance) {
        // 累加进度
        int current = instance.getProgress() + event.getValue();
        instance.setProgress(current);

        JSONObject ruleJson = com.alibaba.fastjson.JSON.parseObject(taskConfig.getRuleConfig());
        int target = ruleJson.getIntValue("targetValue");

        if (current >= target) {
            // 扣减库存，原子操作
            boolean success = taskStockService.deductStock(taskConfig.getId(),1);
            if (success) {
                instance.setStatus(1); // 完成
                return true;
            } else {
                // 库存不足，任务无法完成
                return false;
            }
        }
        return false;
    }
}
