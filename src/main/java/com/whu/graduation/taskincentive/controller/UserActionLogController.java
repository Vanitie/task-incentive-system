package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.UserActionLog;
import com.whu.graduation.taskincentive.service.UserActionLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户行为日志控制器
 */
@RestController
@RequestMapping("/api/user-action-log")
public class UserActionLogController {

    @Autowired
    private UserActionLogService actionLogService;

    @PostMapping("/create")
    public boolean create(@RequestBody UserActionLog log){
        return actionLogService.save(log);
    }

    @GetMapping("/list/{userId}")
    public List<UserActionLog> listByUser(@PathVariable Long userId){
        return actionLogService.selectByUserId(userId);
    }

    @GetMapping("/by-type")
    public List<UserActionLog> byType(@RequestParam String actionType){
        return actionLogService.selectByActionType(actionType);
    }

    @GetMapping("/count")
    public Long count(@RequestParam Long userId, @RequestParam(required = false) String actionType){
        return actionLogService.countUserAction(userId, actionType);
    }
}
