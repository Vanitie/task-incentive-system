package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户奖励记录控制器
 */
@RestController
@RequestMapping("/api/user-reward")
public class UserRewardController {

    @Autowired
    private UserRewardRecordService recordService;

    @GetMapping("/list/{userId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public PageResult<UserRewardRecord> listByUser(@PathVariable Long userId, @RequestParam(required = false) Integer status,
                                                   @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<UserRewardRecord> p = new Page<>(page, size);
        p = recordService.selectByUserIdPage(p, userId, status);
        return PageResult.<UserRewardRecord>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
    }

    @GetMapping("/unclaimed/{userId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<UserRewardRecord> unclaimed(@PathVariable Long userId){
        return recordService.selectUnclaimedPhysicalReward(userId);
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean create(@RequestBody UserRewardRecord record){
        return recordService.save(record);
    }
}
