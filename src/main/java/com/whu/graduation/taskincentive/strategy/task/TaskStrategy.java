package com.whu.graduation.taskincentive.strategy.task;

import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.event.UserEvent;

import java.util.List;

public interface TaskStrategy {
    /**
     * 执行任务逻辑
     * @param event 用户行为事件
     * @param taskConfig 任务模板
     * @param instance 用户任务实例
     * @return 是否完成任务
     */
    /**
     * 执行任务策略，返回本次事件触发的奖励阶梯序号列表（普通任务为1，阶梯任务为多阶梯，未达成则为空）
     */
    List<Integer> execute(UserEvent event, TaskConfig config, UserTaskInstance instance);
}
