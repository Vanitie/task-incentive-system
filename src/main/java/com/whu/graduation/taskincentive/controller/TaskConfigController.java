package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.dto.PageResult;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * 任务配置控制器
 */
@RestController
@RequestMapping("/api/task-config")
public class TaskConfigController {

    @Autowired
    private TaskConfigService taskConfigService;

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
     * @param taskName 任务名称（可选）
     * @param taskType 任务类型（可选）
     * @param status 任务状态（可选）
     * @param rewardType 奖励类型（可选）
     * @param page 页码
     * @param size 每页数量
     * @param orderByEndTime 排序字段（endTime），可选
     * @param asc 是否升序（true升序，false降序），可选
     * @return 分页结果
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
        // 修正排序字段为数据库字段名
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
}
