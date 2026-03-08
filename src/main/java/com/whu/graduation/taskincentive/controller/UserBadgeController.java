package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.UserBadge;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.UserBadgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户徽章控制器
 */
@RestController
@RequestMapping("/api/user-badge")
public class UserBadgeController {

    @Autowired
    private UserBadgeService userBadgeService;

    @GetMapping("/list/{userId}")
    public PageResult<UserBadge> listByUser(@PathVariable Long userId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<UserBadge> p = new Page<>(page, size);
        p = userBadgeService.selectByUserIdPage(p, userId);
        return PageResult.<UserBadge>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
    }

    @PostMapping("/grant")
    public boolean grant(@RequestParam Long userId, @RequestParam Integer badgeCode){
        return userBadgeService.grantBadge(userId, badgeCode);
    }

    @PostMapping("/create")
    public boolean create(@RequestBody UserBadge userBadge){
        return userBadgeService.save(userBadge);
    }
}
