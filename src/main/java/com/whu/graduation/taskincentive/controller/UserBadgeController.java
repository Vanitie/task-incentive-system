package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.UserBadge;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.UserBadgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PageResult<UserBadge>> listByUser(@PathVariable Long userId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<UserBadge> p = new Page<>(page, size);
        p = userBadgeService.selectByUserIdPage(p, userId);
        PageResult<UserBadge> pr = PageResult.<UserBadge>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    @PostMapping("/grant")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> grant(@RequestParam Long userId, @RequestParam Integer badgeCode){
        return ApiResponse.success(userBadgeService.grantBadge(userId, badgeCode));
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> create(@RequestBody UserBadge userBadge){
        return ApiResponse.success(userBadgeService.save(userBadge));
    }
}
