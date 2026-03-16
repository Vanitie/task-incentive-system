package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.BarChartData;
import com.whu.graduation.taskincentive.dto.DailyStatItem;
import com.whu.graduation.taskincentive.dto.ProgressDataItem;
import com.whu.graduation.taskincentive.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /**
     * 两周任务接取数/完成数（本周、上周）
     */
    @GetMapping("/daily-task-bar")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ApiResponse<List<BarChartData>> getTwoWeeksTaskReceiveAndComplete(){
        return ApiResponse.success(statsService.getTwoWeeksTaskReceiveAndComplete());
    }

    /**
     * 本周每日完成率（百分比及颜色）
     */
    @GetMapping("/weekly-completion")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ApiResponse<List<ProgressDataItem>> getThisWeekCompletionPercent(){
        return ApiResponse.success(statsService.getThisWeekCompletionPercent());
    }

    /**
     * 分页查询每日统计
     */
    @GetMapping("/daily-stats")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ApiResponse<Page<DailyStatItem>> pagedDailyStats(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size){
        return ApiResponse.success(statsService.pagedDailyStats(page, size));
    }
}
