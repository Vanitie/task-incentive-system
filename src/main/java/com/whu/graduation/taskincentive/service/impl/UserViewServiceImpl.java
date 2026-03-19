package com.whu.graduation.taskincentive.service.impl;

import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dto.TaskView;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import com.whu.graduation.taskincentive.service.TaskStockService;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import com.whu.graduation.taskincentive.service.UserViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户视图聚合服务实现
 */
@Service
@RequiredArgsConstructor
public class UserViewServiceImpl implements UserViewService {

    @Autowired
    private TaskConfigService taskConfigService;

    @Autowired
    private TaskStockService taskStockService;

    @Autowired
    private UserTaskInstanceService userTaskInstanceService;

    @Override
    public List<TaskView> listAvailableTasks(Long userId, String state) {
        List<TaskConfig> configs = taskConfigService.listAll();
        List<UserTaskInstance> userInstances = userTaskInstanceService.selectByUserId(userId);
        Set<Long> acceptedIds = userInstances == null ? java.util.Collections.emptySet()
                : userInstances.stream().filter(i -> i.getStatus() != null && i.getStatus() > 0)
                .map(UserTaskInstance::getTaskId).collect(Collectors.toSet());

        List<TaskView> views = new ArrayList<>();
        Date now = new Date();
        for (TaskConfig cfg : configs) {
            TaskView v = new TaskView();
            v.setTaskConfig(cfg);
            v.setUserAccepted(acceptedIds.contains(cfg.getId()));

            boolean canAccept = true;
            String reason = null;

            if (cfg.getStatus() == null || cfg.getStatus() != 1) {
                canAccept = false;
                reason = "任务未启用";
            }
            if (cfg.getStartTime() != null && now.before(cfg.getStartTime())) {
                canAccept = false;
                reason = "任务尚未开始";
            }
            if (cfg.getEndTime() != null && now.after(cfg.getEndTime())) {
                canAccept = false;
                reason = "任务已结束";
            }

            if ("LIMITED".equalsIgnoreCase(cfg.getTaskType())) {
                TaskStock stock = taskStockService.getByIdAndStageIndex(cfg.getId(),1);
                Integer remaining = stock == null ? null : stock.getAvailableStock();
                v.setRemainingStock(remaining);
                if (remaining == null || remaining <= 0) {
                    canAccept = false;
                    reason = "库存不足";
                }
            }

            if (v.getUserAccepted() != null && v.getUserAccepted()) {
                canAccept = false;
                reason = "已领取";
            }

            v.setCanAccept(canAccept);
            v.setReason(reason);
            views.add(v);
        }

        if (state != null && !state.isEmpty()) {
            final String s = state.trim().toLowerCase();
            if ("ongoing".equals(s)) {
                views = views.stream().filter(t -> Boolean.TRUE.equals(t.getCanAccept())).collect(Collectors.toList());
            } else if ("completed".equals(s)) {
                views = views.stream().filter(t -> Boolean.TRUE.equals(t.getUserAccepted()))
                        .collect(Collectors.toList());
            } else if ("expired".equals(s)) {
                views = views.stream().filter(t -> t.getTaskConfig().getEndTime() != null && t.getTaskConfig().getEndTime().before(new Date()))
                        .collect(Collectors.toList());
            }
        }

        return views;
    }

    @Override
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<TaskView> listAvailableTasksPage(com.baomidou.mybatisplus.extension.plugins.pagination.Page<TaskView> page, Long userId, String state) {
        // Use TaskConfig pagination to avoid loading all configs
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TaskConfig> cfgPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page.getCurrent(), page.getSize());
        cfgPage = taskConfigService.selectPage(cfgPage);

        List<TaskConfig> configs = cfgPage.getRecords();
        List<UserTaskInstance> userInstances = userTaskInstanceService.selectByUserId(userId);
        Set<Long> acceptedIds = userInstances == null ? java.util.Collections.emptySet()
                : userInstances.stream().filter(i -> i.getStatus() != null && i.getStatus() > 0)
                .map(UserTaskInstance::getTaskId).collect(Collectors.toSet());

        List<TaskView> views = new ArrayList<>();
        Date now = new Date();
        for (TaskConfig cfg : configs) {
            TaskView v = new TaskView();
            v.setTaskConfig(cfg);
            v.setUserAccepted(acceptedIds.contains(cfg.getId()));

            boolean canAccept = true;
            String reason = null;

            if (cfg.getStatus() == null || cfg.getStatus() != 1) {
                canAccept = false;
                reason = "任务未启用";
            }
            if (cfg.getStartTime() != null && now.before(cfg.getStartTime())) {
                canAccept = false;
                reason = "任务尚未开始";
            }
            if (cfg.getEndTime() != null && now.after(cfg.getEndTime())) {
                canAccept = false;
                reason = "任务已结束";
            }

            if ("LIMITED".equalsIgnoreCase(cfg.getTaskType())) {
                TaskStock stock = taskStockService.getByIdAndStageIndex(cfg.getId(),1);
                Integer remaining = stock == null ? null : stock.getAvailableStock();
                v.setRemainingStock(remaining);
                if (remaining == null || remaining <= 0) {
                    canAccept = false;
                    reason = "库存不足";
                }
            }

            if (v.getUserAccepted() != null && v.getUserAccepted()) {
                canAccept = false;
                reason = "已领取";
            }

            v.setCanAccept(canAccept);
            v.setReason(reason);
            views.add(v);
        }

        // apply state filter on the paged items (note: paging already applied at TaskConfig level)
        if (state != null && !state.isEmpty()) {
            final String s = state.trim().toLowerCase();
            if ("ongoing".equals(s)) {
                views = views.stream().filter(t -> Boolean.TRUE.equals(t.getCanAccept())).collect(Collectors.toList());
            } else if ("completed".equals(s)) {
                views = views.stream().filter(t -> Boolean.TRUE.equals(t.getUserAccepted())).collect(Collectors.toList());
            } else if ("expired".equals(s)) {
                views = views.stream().filter(t -> t.getTaskConfig().getEndTime() != null && t.getTaskConfig().getEndTime().before(new Date())).collect(Collectors.toList());
            }
        }

        // construct Page<TaskView> to return
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TaskView> out = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        out.setCurrent(cfgPage.getCurrent());
        out.setSize(cfgPage.getSize());
        out.setTotal(cfgPage.getTotal());
        out.setRecords(views);
        return out;
    }
}
