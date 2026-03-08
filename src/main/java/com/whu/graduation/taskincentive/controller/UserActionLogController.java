package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.UserActionLog;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.UserActionLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public boolean create(@RequestBody UserActionLog log){
        return actionLogService.save(log);
    }

    @GetMapping("/list/{userId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public PageResult<UserActionLog> listByUser(@PathVariable Long userId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<UserActionLog> p = new Page<>(page, size);
        p = actionLogService.selectByUserIdPage(p, userId);
        return PageResult.<UserActionLog>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
    }

    @GetMapping("/by-type")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public PageResult<UserActionLog> byType(@RequestParam String actionType, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<UserActionLog> p = new Page<>(page, size);
        p = actionLogService.selectByActionTypePage(p, actionType);
        return PageResult.<UserActionLog>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Long count(@RequestParam Long userId, @RequestParam(required = false) String actionType){
        return actionLogService.countUserAction(userId, actionType);
    }
}
