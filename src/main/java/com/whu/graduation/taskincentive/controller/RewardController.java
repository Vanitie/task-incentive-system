package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dto.Reward;
import com.whu.graduation.taskincentive.service.RewardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 奖励相关控制器（主动触发发放）
 */
@RestController
@RequestMapping("/api/reward")
public class RewardController {

    @Autowired
    private RewardService rewardService;

    /** 主动发放奖励（主要用于测试或后台操作） */
    @PostMapping("/grant")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean grant(@RequestParam Long userId, @RequestBody Reward reward){
        return rewardService.grantReward(userId, reward);
    }
}
