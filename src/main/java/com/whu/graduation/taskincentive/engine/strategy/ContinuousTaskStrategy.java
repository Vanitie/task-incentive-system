package com.whu.graduation.taskincentive.engine.strategy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.event.UserEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 连续签到任务策略
 */
@Slf4j
@Component("CONTINUOUS")
public class ContinuousTaskStrategy implements TaskStrategy {

    @Override
    public boolean execute(UserEvent event, TaskConfig taskConfig, UserTaskInstance instance) {
        // 1. 解析 extraData
        JSONObject extra = instance.getExtraData() != null && !instance.getExtraData().isEmpty()
                ? JSON.parseObject(instance.getExtraData())
                : new JSONObject();

        LocalDate lastDate = extra.getObject("lastSignDate", LocalDate.class);
        int continuous = extra.getIntValue("continuousDays");

        LocalDate today = event.getTime().toLocalDate();

        if (lastDate != null && lastDate.plusDays(1).equals(today)) {
            continuous++;
        } else {
            continuous = 1;
        }

        extra.put("lastSignDate", today);
        extra.put("continuousDays", continuous);
        instance.setExtraData(extra.toJSONString()); // 存回 String

        // 2. 从 ruleConfig 获取目标天数
        JSONObject ruleJson = JSON.parseObject(taskConfig.getRuleConfig());
        int targetDays = ruleJson.getIntValue("targetDays");

        if (continuous >= targetDays) {
            instance.setStatus(1); // 完成
            return true;
        }

        return false;
    }
}
