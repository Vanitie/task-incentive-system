package com.whu.graduation.taskincentive.strategy.task;

import com.alibaba.fastjson.JSON;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dto.StairExtraData;
import com.whu.graduation.taskincentive.dto.StairRuleConfig;
import com.whu.graduation.taskincentive.event.UserEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 阶梯任务策略实现
 * 1. 解析 ruleConfig 获取所有阶梯目标
 * 2. 计算当前进度，判断本次事件后达成哪些阶梯
 * 3. 检查已发放的阶段（extraData 记录已发放的阶梯序号）
 * 4. 返回本次新达成且未发放奖励的所有阶梯序号
 */
@Slf4j
@Component("STAIR")
public class StairTaskStrategy implements TaskStrategy {

    @Override
    public List<Integer> execute(UserEvent event, TaskConfig config, UserTaskInstance instance) {
        StairRuleConfig rule = null;
        try {
            rule = JSON.parseObject(config.getRuleConfig(), StairRuleConfig.class);
        } catch (Exception e) {
            log.warn("stair rule parse failed, taskId={}, err={}", config.getId(), e.getMessage());
        }
        if (rule == null || rule.getStages() == null || rule.getStages().isEmpty()) return Collections.emptyList();
        List<Integer> stages = rule.getStages();

        // 计算本次进度
        int oldProgress = instance.getProgress() == null ? 0 : instance.getProgress();
        int newProgress = oldProgress + (event.getValue() == null ? 0 : event.getValue());
        instance.setProgress(newProgress);

        // extraData 记录已发放的阶梯序号（如{"grantedStages":[1,2]})
        Set<Integer> granted = new HashSet<>();
        if (instance.getExtraData() != null && !instance.getExtraData().isEmpty()) {
            try {
                StairExtraData extra = JSON.parseObject(instance.getExtraData(), StairExtraData.class);
                if (extra != null && extra.getGrantedStages() != null) {
                    granted.addAll(extra.getGrantedStages());
                }
            } catch (Exception e) {
                log.debug("stair extra parse failed, taskId={}, err={}", config.getId(), e.getMessage());
            }
        }

        // 计算本次新达成的阶梯序号
        List<Integer> newlyGranted = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            int stageTarget = stages.get(i);
            int stageIndex = i + 1;
            if (newProgress >= stageTarget && !granted.contains(stageIndex)) {
                newlyGranted.add(stageIndex);
                granted.add(stageIndex);
            }
        }

        StairExtraData newExtra = new StairExtraData();
        newExtra.setGrantedStages(granted);
        instance.setExtraData(JSON.toJSONString(newExtra));

        return newlyGranted;
    }
}
