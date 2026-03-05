package com.whu.graduation.taskincentive.strategy.task;

import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.event.UserEvent;

public interface TaskStrategy {
    /**
     * 执行任务逻辑
     * @param event 用户行为事件
     * @param taskConfig 任务模板
     * @param instance 用户任务实例
     * @return 是否完成任务
     */
    boolean execute(UserEvent event, TaskConfig taskConfig, UserTaskInstance instance);
}
