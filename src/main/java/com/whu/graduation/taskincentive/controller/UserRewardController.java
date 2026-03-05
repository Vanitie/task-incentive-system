package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public List<UserRewardRecord> listByUser(@PathVariable Long userId){
        return recordService.selectByUserId(userId);
    }

    @GetMapping("/unclaimed/{userId}")
    public List<UserRewardRecord> unclaimed(@PathVariable Long userId){
        return recordService.selectUnclaimedPhysicalReward(userId);
    }

    @PostMapping("/create")
    public boolean create(@RequestBody UserRewardRecord record){
        return recordService.save(record);
    }
}
