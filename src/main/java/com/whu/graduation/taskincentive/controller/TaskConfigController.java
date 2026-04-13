package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.entity.TaskConfigHistory;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.dto.TaskAnalyticsDTO;
import com.whu.graduation.taskincentive.service.TaskAnalyticsService;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务配置控制器
 */
@RestController
@RequestMapping("/api/task-config")
public class TaskConfigController {

    @Autowired
    private TaskConfigService taskConfigService;

    @Autowired
    private TaskAnalyticsService taskAnalyticsService;

    /** 查询任务配置（分页） */
    @GetMapping("/list")
    public ApiResponse<PageResult<TaskConfig>> listAll(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) Integer status){
        if (status != null) {
            Page<TaskConfig> p = new Page<>(page, size);
            p = taskConfigService.selectByStatusPage(p, status);
            PageResult<TaskConfig> pr = PageResult.<TaskConfig>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
            return ApiResponse.success(pr);
        }

        Page<TaskConfig> p = new Page<>(page, size);
        p = taskConfigService.selectPage(p);
        PageResult<TaskConfig> pr = PageResult.<TaskConfig>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        return ApiResponse.success(pr);
    }

    /** 根据ID查询 */
    @GetMapping("/{id}")
    public ApiResponse<TaskConfig> getById(@PathVariable Long id){
        return ApiResponse.success(taskConfigService.getById(id));
    }

    /** 新增任务配置 */
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> create(@RequestBody TaskConfig taskConfig){
        return ApiResponse.success(taskConfigService.save(taskConfig));
    }

    /** 更新任务配置 */
    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> update(@RequestBody TaskConfig taskConfig){
        return ApiResponse.success(taskConfigService.update(taskConfig));
    }

    /** 删除任务配置 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> delete(@PathVariable Long id){
        return ApiResponse.success(taskConfigService.deleteById(id));
    }

    /**
     * 多条件分页查询任务配置
     */
    @GetMapping("/search")
    public ApiResponse<PageResult<TaskConfig>> search(
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String rewardType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "endTime") String orderByEndTime,
            @RequestParam(defaultValue = "false") boolean asc
    ) {
        Page<TaskConfig> p = new Page<>(page, size);
        String orderColumn = "endTime".equals(orderByEndTime) ? "end_time" : orderByEndTime;
        if (asc) {
            p.addOrder(com.baomidou.mybatisplus.core.metadata.OrderItem.asc(orderColumn));
        } else {
            p.addOrder(com.baomidou.mybatisplus.core.metadata.OrderItem.desc(orderColumn));
        }
        Page<TaskConfig> result = taskConfigService.searchByConditions(taskName, taskType, status, rewardType, p);
        PageResult<TaskConfig> pr = PageResult.<TaskConfig>builder()
                .total(result.getTotal())
                .page((int)result.getCurrent())
                .size((int)result.getSize())
                .items(result.getRecords())
                .build();
        return ApiResponse.success(pr);
    }

    @GetMapping("/{id}/analytics/overview")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<TaskAnalyticsDTO.MetricOverview> analyticsOverview(@PathVariable Long id,
                                                                          @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(taskAnalyticsService.taskConfigOverview(id, days));
    }

    @GetMapping("/{id}/analytics/audience-hit")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<TaskAnalyticsDTO.AudienceHit> analyticsAudienceHit(@PathVariable Long id,
                                                                          @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(taskAnalyticsService.taskConfigAudienceHit(id, days));
    }

    @GetMapping("/{id}/analytics/time-heatmap")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<TaskAnalyticsDTO.HeatmapCell>> analyticsTimeHeatmap(@PathVariable Long id,
                                                                                 @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(taskAnalyticsService.taskConfigTimeHeatmap(id, days));
    }

    @GetMapping("/{id}/analytics/reward-elasticity")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<TaskAnalyticsDTO.RewardElasticityItem>> analyticsRewardElasticity(@PathVariable Long id,
                                                                                               @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(taskAnalyticsService.taskConfigRewardElasticity(id, days));
    }

    @GetMapping("/{id}/analytics/version-compare")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<TaskAnalyticsDTO.VersionCompare> analyticsVersionCompare(@PathVariable Long id,
                                                                                @RequestParam(required = false) String baselineVersion,
                                                                                @RequestParam(required = false) String compareVersion,
                                                                                @RequestParam(required = false) String compareStart,
                                                                                @RequestParam(required = false) String compareEnd) {
        return ApiResponse.success(taskAnalyticsService.taskConfigVersionCompare(id, baselineVersion, compareVersion, compareStart, compareEnd));
    }

    @GetMapping("/{id}/analytics/health")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<TaskAnalyticsDTO.HealthMetrics> analyticsHealth(@PathVariable Long id,
                                                                       @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(taskAnalyticsService.taskConfigHealth(id, days));
    }

    @GetMapping("/{id}/analytics/insight")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<TaskAnalyticsDTO.TaskConfigInsight> analyticsInsight(@PathVariable Long id,
                                                                             @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(taskAnalyticsService.taskConfigInsight(id, days));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<TaskConfigHistory>> history(@PathVariable Long id) {
        return ApiResponse.success(taskConfigService.listHistoryByTaskId(id));
    }
}
