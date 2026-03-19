package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.dto.ApiResponse;
import com.whu.graduation.taskincentive.service.TaskStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务库存控制器
 */
@RestController
@RequestMapping("/api/task-stock")
public class TaskStockController {

    @Autowired
    private TaskStockService taskStockService;

    @GetMapping("/{taskId}")
    public ApiResponse<List<TaskStock>> get(@PathVariable Long taskId){
        return ApiResponse.success(taskStockService.getById(taskId));
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> create(@RequestBody TaskStock stock){
        return ApiResponse.success(taskStockService.save(stock));
    }

    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> update(@RequestBody TaskStock stock){
        return ApiResponse.success(taskStockService.update(stock));
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Boolean> delete(@PathVariable Long taskId){
        return ApiResponse.success(taskStockService.deleteById(taskId));
    }
}
