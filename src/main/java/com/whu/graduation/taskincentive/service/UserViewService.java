package com.whu.graduation.taskincentive.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dto.TaskView;

import java.util.List;

/**
 * 用户视图聚合服务
 */
public interface UserViewService {

    /**
     * 列出对用户可见的任务视图（包含是否可领取、剩余库存、已接取状态、原因）
     * @param state 可选筛选："ongoing"（进行中，可领取）、"completed"（已完成）、"expired"（已过期）
     */
    List<TaskView> listAvailableTasks(Long userId, String state);

    /**
     * 分页获取可见任务视图
     */
    Page<TaskView> listAvailableTasksPage(Page<TaskView> page, Long userId, String state);
}
