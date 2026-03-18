package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dto.ApiResponse;
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
    public ApiResponse<PageResult<UserRewardRecord>> listByUser(@PathVariable Long userId, @RequestParam(required = false) Integer status,
                                                   @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<UserRewardRecord> p = new Page<>(page, size);
        p = recordService.selectByUserIdPage(p, userId, status);
        PageResult<UserRewardRecord> pr = PageResult.<UserRewardRecord>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    @GetMapping("/unclaimed/{userId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<UserRewardRecord>> unclaimed(@PathVariable Long userId){
        return ApiResponse.success(recordService.selectUnclaimedPhysicalReward(userId));
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> create(@RequestBody UserRewardRecord record){
        return ApiResponse.success(recordService.save(record));
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
    public ApiResponse<ChartData> countTodayReceivers(){
        List<Long> data = recordService.getReceivedUsersLast7Days();
        long value = data.get(data.size() - 1);
        String percent = computePercent(data);
        ChartData chart = ChartData.builder()
                .icon("reward")
                .name("今日奖励领取用户数")
                .value(value)
                .data(data)
                .percent(percent)
                .bgColor("#fffbe6")
                .color("#ffb300")
                .duration(1800)
                .build();
        return ApiResponse.success(chart);
    }

    /**
     * 用户奖励记录列表接口，支持按用户ID、任务ID、奖励类型、奖励状态组合条件分页查询
     */
    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PageResult<UserRewardRecord>> listByConditions(@RequestParam(required = false) Long userId,
                                                                     @RequestParam(required = false) Long taskId,
                                                                     @RequestParam(required = false) String rewardType,
                                                                     @RequestParam(required = false) Integer status,
                                                                     @RequestParam(defaultValue = "1") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        Page<UserRewardRecord> p = new Page<>(page, size);
        p = recordService.listByConditions(p, userId, taskId, rewardType, status);
        PageResult<UserRewardRecord> pr = PageResult.<UserRewardRecord>builder()
                .total(p.getTotal())
                .page((int)p.getCurrent())
                .size((int)p.getSize())
                .items(p.getRecords())
                .build();
        return ApiResponse.success(pr);
    }
}
