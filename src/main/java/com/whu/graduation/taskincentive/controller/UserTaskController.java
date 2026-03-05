package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
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

    /** 用户接取任务（幂等） */
    @PostMapping("/accept")
    public UserTaskInstance acceptTask(@RequestParam Long userId, @RequestParam Long taskId){
        return instanceService.acceptTask(userId, taskId);
    }

    /** 获取用户已接取任务的实例 */
    @GetMapping("/accepted")
    public UserTaskInstance getAccepted(@RequestParam Long userId, @RequestParam Long taskId){
        return instanceService.getAcceptedInstance(userId, taskId);
    }

    /** 获取用户任务列表 */
    @GetMapping("/list/{userId}")
    public List<UserTaskInstance> listByUser(@PathVariable Long userId){
        return instanceService.selectByUserId(userId);
    }

    /** 手动触发同步到持久层（主要用于测试或补偿） */
    @PostMapping("/publish")
    public void publish(@RequestBody UserTaskInstance instance){
        instanceService.updateAndPublish(instance);
    }
}
