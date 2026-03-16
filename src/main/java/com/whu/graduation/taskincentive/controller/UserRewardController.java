package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dto.ChartData;
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

    // helper
    private String computePercent(List<Long> data){
        if (data == null || data.size() < 2) return "+0%";
        int n = data.size();
        long last = data.get(n-1);
        long prev = data.get(n-2);
        if (prev == 0) return last == 0 ? "+0%" : "+100%";
        double p = ((double)(last - prev) / (double)prev) * 100.0;
        String sign = p >= 0 ? "+" : "";
        return String.format("%s%.0f%%", sign, p);
    }

    /**
     * 统计过去7天内每天领取奖励的不重复用户数，返回 ChartData
     */
    @GetMapping("/count/today-receivers")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ChartData countTodayReceivers(){
        List<Long> data = recordService.getReceivedUsersLast7Days();
        long value = data.stream().mapToLong(Long::longValue).sum();
        String percent = computePercent(data);
        return ChartData.builder()
                .name("今日奖励领取用户数")
                .value(value)
                .data(data)
                .percent(percent)
                .bgColor("#fffbe6")
                .color("#ffb300")
                .duration(1800)
                .build();
    }
}
