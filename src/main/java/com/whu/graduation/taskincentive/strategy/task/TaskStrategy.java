package com.whu.graduation.taskincentive.strategy.task;

import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.event.UserEvent;

import java.util.List;

public interface TaskStrategy {
    /**
     * 执行任务策略，返回本次事件触发的奖励阶梯序号列表。
     * 普通任务返回[1]，阶梯任务返回多阶梯序号，未达成则返回空集合。
     */
    List<Integer> execute(UserEvent event, TaskConfig config, UserTaskInstance instance);
}
