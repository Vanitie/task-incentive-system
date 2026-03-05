package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.UserBadge;
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
    public List<UserBadge> listByUser(@PathVariable Long userId){
        return userBadgeService.listByUserId(userId);
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
