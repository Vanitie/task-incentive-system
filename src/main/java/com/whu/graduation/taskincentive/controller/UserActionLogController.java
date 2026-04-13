package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.UserActionLog;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
    import com.whu.graduation.taskincentive.dto.UserActionAnalyticsDTO;
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
    public ApiResponse<Boolean> create(@RequestBody UserActionLog log){
        return ApiResponse.success(actionLogService.save(log));
    }

    @GetMapping("/list/{userId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PageResult<UserActionLog>> listByUser(@PathVariable Long userId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<UserActionLog> p = new Page<>(page, size);
        p = actionLogService.selectByUserIdPage(p, userId);
        PageResult<UserActionLog> pr = PageResult.<UserActionLog>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    @GetMapping("/by-type")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PageResult<UserActionLog>> byType(@RequestParam String actionType, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<UserActionLog> p = new Page<>(page, size);
        p = actionLogService.selectByActionTypePage(p, actionType);
        PageResult<UserActionLog> pr = PageResult.<UserActionLog>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Long> count(@RequestParam Long userId, @RequestParam(required = false) String actionType){
        return ApiResponse.success(actionLogService.countUserAction(userId, actionType));
    }

    /**
     * 组合条件分页查询用户行为日志
     */
    @GetMapping("/query")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PageResult<UserActionLog>> queryByConditions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UserActionLog> p = new Page<>(page, size);
        p = actionLogService.queryByConditions(p, userId, actionType, startTime, endTime);
        PageResult<UserActionLog> pr = PageResult.<UserActionLog>builder()
                .total(p.getTotal())
                .page((int)p.getCurrent())
                .size((int)p.getSize())
                .items(p.getRecords())
                .build();
        return ApiResponse.success(pr);
    }

    /**
     * 行为总量趋势（按天/小时）
     */
    @GetMapping("/analytics/trend")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<UserActionAnalyticsDTO.TrendPoint>> trend(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "DAY") String granularity) {
        return ApiResponse.success(actionLogService.getActionTrend(startTime, endTime, granularity));
    }

    /**
     * 行为类型占比（签到/学习/接任务/领奖）
     */
    @GetMapping("/analytics/type-ratio")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<UserActionAnalyticsDTO.TypeRatioItem>> typeRatio(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.success(actionLogService.getActionTypeRatio(startTime, endTime));
    }

    /**
     * 用户分层行为（新用户/活跃用户/沉默用户）
     */
    @GetMapping("/analytics/user-layer")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<UserActionAnalyticsDTO.UserLayerItem>> userLayer(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.success(actionLogService.getUserLayerBehavior(startTime, endTime));
    }

    /**
     * 一周 7x24 时间热力图
     */
    @GetMapping("/analytics/heatmap")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<UserActionAnalyticsDTO.HeatmapCell>> heatmap(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.success(actionLogService.getWeeklyHeatmap(startTime, endTime));
    }

    /**
     * 转化效率看板（完成率、领奖率、人均行为次数）
     */
    @GetMapping("/analytics/conversion")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<UserActionAnalyticsDTO.ConversionDashboard> conversion(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.success(actionLogService.getConversionDashboard(startTime, endTime));
    }

    /**
     * TopN 榜单（行为次数最高用户/行为类型）
     */
    @GetMapping("/analytics/topn")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<UserActionAnalyticsDTO.TopNResult> topN(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "10") Integer n) {
        return ApiResponse.success(actionLogService.getTopN(startTime, endTime, n));
    }
}
