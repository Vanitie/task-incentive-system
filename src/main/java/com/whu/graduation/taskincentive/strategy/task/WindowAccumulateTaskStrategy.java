package com.whu.graduation.taskincentive.strategy.task;

import com.alibaba.fastjson.JSON;
import com.whu.graduation.taskincentive.common.enums.UserTaskStatus;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dto.WindowAccumulateEntry;
import com.whu.graduation.taskincentive.dto.WindowAccumulateExtraData;
import com.whu.graduation.taskincentive.dto.WindowAccumulateRuleConfig;
import com.whu.graduation.taskincentive.event.UserEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 时间窗累积任务策略
 */
@Slf4j
@Component("WINDOW_ACCUMULATE")
public class WindowAccumulateTaskStrategy implements TaskStrategy {

    @Override
    public List<Integer> execute(UserEvent event, TaskConfig taskConfig, UserTaskInstance instance) {
        WindowAccumulateRuleConfig rule = null;
        try {
            rule = JSON.parseObject(taskConfig.getRuleConfig(), WindowAccumulateRuleConfig.class);
        } catch (Exception e) {
            log.warn("window accumulate rule parse failed, taskId={}, err={}", taskConfig.getId(), e.getMessage());
        }
        if (rule == null || rule.getTargetValue() == null) return Collections.emptyList();
        Integer windowMinutes = rule.getWindowMinutes();
        if (windowMinutes == null || windowMinutes <= 0) return Collections.emptyList();

        LocalDateTime now = event.getTime() == null ? LocalDateTime.now() : event.getTime();
        int value = event.getValue() == null ? 0 : event.getValue();

        WindowAccumulateExtraData extra = null;
        if (instance.getExtraData() != null && !instance.getExtraData().isEmpty()) {
            try {
                extra = JSON.parseObject(instance.getExtraData(), WindowAccumulateExtraData.class);
            } catch (Exception e) {
                log.debug("window accumulate extra parse failed, taskId={}, err={}", taskConfig.getId(), e.getMessage());
            }
        }
        if (extra == null) {
            extra = new WindowAccumulateExtraData();
        }

        List<WindowAccumulateEntry> entries = extra.getEntries();
        if (entries == null) entries = new ArrayList<>();
        entries.add(new WindowAccumulateEntry(now, value));

        LocalDateTime cutoff = now.minusMinutes(windowMinutes);
        int sum = 0;
        List<WindowAccumulateEntry> kept = new ArrayList<>();
        for (WindowAccumulateEntry entry : entries) {
            if (entry == null || entry.getTime() == null) continue;
            if (!entry.getTime().isBefore(cutoff)) {
                int v = entry.getValue() == null ? 0 : entry.getValue();
                sum += v;
                kept.add(entry);
            }
        }

        extra.setEntries(kept);
        instance.setExtraData(JSON.toJSONString(extra));
        instance.setProgress(sum);

        if (sum >= rule.getTargetValue()) {
            instance.setStatus(UserTaskStatus.COMPLETED.getCode());
            return List.of(1);
        }

        return Collections.emptyList();
    }
}
