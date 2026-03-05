package com.whu.graduation.taskincentive.strategy.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.event.UserEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 阶梯任务策略
 */
@Slf4j
@Component("STAIR")
public class StairTaskStrategy implements TaskStrategy {

    @Override
    public boolean execute(UserEvent event, TaskConfig taskConfig, UserTaskInstance instance) {
        int current = instance.getProgress() + event.getValue();
        instance.setProgress(current);

        // 解析 extraData
        JSONObject extra = instance.getExtraData() != null && !instance.getExtraData().isEmpty()
                ? JSON.parseObject(instance.getExtraData())
                : new JSONObject();

        JSONArray rewardedStages = extra.getJSONArray("rewardedStages") != null
                ? extra.getJSONArray("rewardedStages") : new JSONArray();

        JSONObject ruleJson = JSON.parseObject(taskConfig.getRuleConfig());
        JSONArray stages = ruleJson.getJSONArray("stages"); // 阶梯值 [10,50,100]

        for (int i = 0; i < stages.size(); i++) {
            int stageTarget = stages.getIntValue(i);
            if (current >= stageTarget && !rewardedStages.contains(stageTarget)) {
                rewardedStages.add(stageTarget);
                // 奖励发放由 TaskEngine 处理
            }
        }

        extra.put("rewardedStages", rewardedStages);
        instance.setExtraData(extra.toJSONString()); // 存回 String

        int finalTarget = stages.getIntValue(stages.size() - 1);
        if (current >= finalTarget) {
            instance.setStatus(1); // 完成
            return true;
        }

        return false;
    }
}
