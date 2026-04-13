package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.dto.TaskView;
import com.whu.graduation.taskincentive.dto.TaskAnalyticsDTO;
import com.whu.graduation.taskincentive.dto.UserActionAnalyticsDTO;
import com.whu.graduation.taskincentive.service.TaskAnalyticsService;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import com.whu.graduation.taskincentive.service.UserViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户任务控制器：包含用户接取任务、获取进度、更新等
 */
@RestController
@RequestMapping("/api/user-task")
public class UserTaskController {

    @Autowired
    private UserTaskInstanceService instanceService;

    @Autowired
    private UserViewService userViewService;

    @Autowired
    private TaskAnalyticsService taskAnalyticsService;

    /** 用户接取任务（幂等） */
    @PostMapping("/accept")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<UserTaskInstance> acceptTask(@RequestParam Long userId, @RequestParam Long taskId){
        return ApiResponse.success(instanceService.acceptTask(userId, taskId));
    }

    /** 获取用户已接取任务的实例（可选按 status 筛选） */
    @GetMapping("/accepted/{userId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PageResult<UserTaskInstance>> getAccepted(@PathVariable Long userId, @RequestParam(required = false) Integer status,
                                                    @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<UserTaskInstance> p = new Page<>(page, size);
        p = instanceService.selectByUserIdPage(p, userId, status);
        PageResult<UserTaskInstance> pr = PageResult.<UserTaskInstance>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    /** 获取用户任务列表 */
    @GetMapping("/list/{userId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PageResult<UserTaskInstance>> listByUser(@PathVariable Long userId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size){
        Page<UserTaskInstance> p = new Page<>(page, size);
        p = instanceService.selectByUserIdPage(p, userId, null);
        PageResult<UserTaskInstance> pr = PageResult.<UserTaskInstance>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    /** 获取用户可领取任务的聚合视图 */
    @GetMapping("/available/{userId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PageResult<TaskView>> listAvailableTasks(@PathVariable Long userId, @RequestParam(required = false) String state,
                                                   @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        Page<TaskView> p = new Page<>(page, size);
        p = userViewService.listAvailableTasksPage(p, userId, state);
        PageResult<TaskView> pr = PageResult.<TaskView>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    /**
     * 用户任务实例列表接口，支持按用户ID、任务ID、任务状态组合条件分页查询
     */
    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<PageResult<UserTaskInstance>> listByConditions(@RequestParam(required = false) Long userId,
                                                                     @RequestParam(required = false) Long taskId,
                                                                     @RequestParam(required = false) Integer status,
                                                                     @RequestParam(defaultValue = "1") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        Page<UserTaskInstance> p = new Page<>(page, size);
        p = instanceService.listByConditions(p, userId, taskId, status);
        PageResult<UserTaskInstance> pr = PageResult.<UserTaskInstance>builder()
                .total(p.getTotal())
                .page((int)p.getCurrent())
                .size((int)p.getSize())
                .items(p.getRecords())
                .build();
        return ApiResponse.success(pr);
    }

    /** 手动触发同步到持久层（主要用于测试或补偿） */
    @PostMapping("/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> publish(@RequestBody UserTaskInstance instance){
        instanceService.updateAndPublish(instance);
        return ApiResponse.success();
    }

    @GetMapping("/analytics/overview")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<TaskAnalyticsDTO.MetricOverview> analyticsOverview(@RequestParam(required = false) String startTime,
                                                                          @RequestParam(required = false) String endTime,
                                                                          @RequestParam(required = false) String taskType,
                                                                          @RequestParam(required = false) String campaignId,
                                                                          @RequestParam(required = false) String userLayer) {
        return ApiResponse.success(taskAnalyticsService.userTaskOverview(startTime, endTime, taskType, campaignId, userLayer));
    }

    @GetMapping("/analytics/funnel")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<TaskAnalyticsDTO.FunnelMetrics> analyticsFunnel(@RequestParam(required = false) String startTime,
                                                                        @RequestParam(required = false) String endTime,
                                                                        @RequestParam(required = false) String taskType,
                                                                        @RequestParam(required = false) String campaignId,
                                                                        @RequestParam(required = false) String userLayer) {
        return ApiResponse.success(taskAnalyticsService.userTaskFunnel(startTime, endTime, taskType, campaignId, userLayer));
    }

    @GetMapping("/analytics/trend")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<TaskAnalyticsDTO.TrendPoint>> analyticsTrend(@RequestParam(required = false) String startTime,
                                                                         @RequestParam(required = false) String endTime,
                                                                         @RequestParam(defaultValue = "DAY") String granularity,
                                                                         @RequestParam(required = false) String taskType,
                                                                         @RequestParam(required = false) String campaignId) {
        return ApiResponse.success(taskAnalyticsService.userTaskTrend(startTime, endTime, granularity, taskType, campaignId));
    }

    @GetMapping("/analytics/type-performance")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<TaskAnalyticsDTO.DimensionPerformance>> analyticsTypePerformance(@RequestParam(required = false) String startTime,
                                                                                              @RequestParam(required = false) String endTime,
                                                                                              @RequestParam(required = false) Integer topN,
                                                                                              @RequestParam(defaultValue = "rewardedCount") String sortBy) {
        return ApiResponse.success(taskAnalyticsService.userTaskTypePerformance(startTime, endTime, topN, sortBy));
    }

    @GetMapping("/analytics/user-layer")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<TaskAnalyticsDTO.DimensionPerformance>> analyticsUserLayer(@RequestParam(required = false) String startTime,
                                                                                        @RequestParam(required = false) String endTime,
                                                                                        @RequestParam(required = false) Integer topN,
                                                                                        @RequestParam(defaultValue = "rewardedCount") String sortBy) {
        return ApiResponse.success(taskAnalyticsService.userTaskLayerPerformance(startTime, endTime, topN, sortBy));
    }

    /**
     * TopN 榜单：行为次数最高用户/任务类型（按接取任务统计）
     */
    @GetMapping("/analytics/topn")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<UserActionAnalyticsDTO.TopNResult> topN(@RequestParam(defaultValue = "10") int n,
                                                               @RequestParam(required = false) String startTime,
                                                               @RequestParam(required = false) String endTime,
                                                               @RequestParam(required = false) String taskType,
                                                               @RequestParam(defaultValue = "acceptedCount") String orderBy) {
        if (startTime == null && endTime == null && (taskType == null || taskType.isEmpty())) {
            return ApiResponse.success(UserActionAnalyticsDTO.TopNResult.builder()
                    .topUsers(instanceService.topAcceptedUsers(n))
                    .topActionTypes(instanceService.topAcceptedTaskTypes(n))
                    .build());
        }
        List<UserActionAnalyticsDTO.TopNItem> users = taskAnalyticsService.userTaskTopN(startTime, endTime, n, taskType, orderBy)
                .stream()
                .map(m -> UserActionAnalyticsDTO.TopNItem.builder()
                        .name(m.getUserName() == null || m.getUserName().isEmpty() ? String.valueOf(m.getUserId()) : m.getUserName())
                        .count(m.getAcceptedCount() == null ? 0L : m.getAcceptedCount())
                        .build())
                .collect(Collectors.toList());
        List<UserActionAnalyticsDTO.TopNItem> actionTypes = instanceService.topAcceptedTaskTypes(n);
        return ApiResponse.success(UserActionAnalyticsDTO.TopNResult.builder().topUsers(users).topActionTypes(actionTypes).build());
    }

    /**
     * TopN 榜单：接取最多任务用户
     */
    @GetMapping("/analytics/topn-accepted")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<TaskAnalyticsDTO.UserTopNItem>> topNAccepted(@RequestParam(defaultValue = "10") int n,
                                                                          @RequestParam(required = false) String startTime,
                                                                          @RequestParam(required = false) String endTime,
                                                                          @RequestParam(required = false) String taskType,
                                                                          @RequestParam(defaultValue = "acceptedCount") String orderBy) {
        return ApiResponse.success(taskAnalyticsService.userTaskTopNAccepted(startTime, endTime, n, taskType, orderBy));
    }

    @GetMapping("/analytics/anomaly")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<TaskAnalyticsDTO.AnomalyMetrics> analyticsAnomaly(@RequestParam(required = false) String startTime,
                                                                          @RequestParam(required = false) String endTime,
                                                                          @RequestParam(required = false) String taskType,
                                                                          @RequestParam(required = false) String campaignId) {
        return ApiResponse.success(taskAnalyticsService.userTaskAnomaly(startTime, endTime, taskType, campaignId));
    }

    @GetMapping("/analytics/cost-roi")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<TaskAnalyticsDTO.CostRoiMetrics> analyticsCostRoi(@RequestParam(required = false) String startTime,
                                                                          @RequestParam(required = false) String endTime,
                                                                          @RequestParam(required = false) String taskType,
                                                                          @RequestParam(required = false) String campaignId) {
        return ApiResponse.success(taskAnalyticsService.userTaskCostRoi(startTime, endTime, taskType, campaignId));
    }
}
