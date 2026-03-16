package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.dto.TaskView;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import com.whu.graduation.taskincentive.service.UserViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户任务控制器：包含用户接取任务、获取进度、更新等
 */
@RestController
@RequestMapping("/api/user-task")
public class UserTaskController {

    @Autowired
    private UserTaskInstanceService instanceService;

    @Autowired
    private UserViewService userViewService;

    /** 用户接取任务（幂等） */
    @PostMapping("/accept")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<UserTaskInstance> acceptTask(@RequestParam Long userId, @RequestParam Long taskId){
        return ApiResponse.success(instanceService.acceptTask(userId, taskId));
    }

    /** 获取用户已接取任务的实例（可选按 status 筛选） */
    @GetMapping("/accepted/{userId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PageResult<UserTaskInstance>> getAccepted(@PathVariable Long userId, @RequestParam(required = false) Integer status,
                                                    @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserTaskInstance> p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        p = instanceService.selectByUserIdPage(p, userId, status);
        PageResult<UserTaskInstance> pr = PageResult.<UserTaskInstance>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    /** 获取用户任务列表 */
    @GetMapping("/list/{userId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PageResult<UserTaskInstance>> listByUser(@PathVariable Long userId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserTaskInstance> p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        p = instanceService.selectByUserIdPage(p, userId, null);
        PageResult<UserTaskInstance> pr = PageResult.<UserTaskInstance>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    /** 获取用户可领取任务的聚合视图 */
    @GetMapping("/available/{userId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PageResult<TaskView>> listAvailableTasks(@PathVariable Long userId, @RequestParam(required = false) String state,
                                                   @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TaskView> p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        p = userViewService.listAvailableTasksPage(p, userId, state);
        PageResult<TaskView> pr = PageResult.<TaskView>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    /** 手动触发同步到持久层（主要用于测试或补偿） */
    @PostMapping("/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> publish(@RequestBody UserTaskInstance instance){
        instanceService.updateAndPublish(instance);
        return ApiResponse.success();
    }
}
