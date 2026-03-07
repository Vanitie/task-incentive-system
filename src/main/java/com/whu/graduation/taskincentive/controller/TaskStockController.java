package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.service.TaskStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 任务库存控制器
 */
@RestController
@RequestMapping("/api/task-stock")
public class TaskStockController {

    @Autowired
    private TaskStockService taskStockService;

    @GetMapping("/{taskId}")
    public TaskStock get(@PathVariable Long taskId){
        return taskStockService.getById(taskId);
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean create(@RequestBody TaskStock stock){
        return taskStockService.save(stock);
    }

    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean update(@RequestBody TaskStock stock){
        return taskStockService.update(stock);
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean delete(@PathVariable Long taskId){
        return taskStockService.deleteById(taskId);
    }
}
