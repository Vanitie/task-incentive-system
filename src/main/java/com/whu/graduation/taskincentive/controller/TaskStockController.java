package com.whu.graduation.taskincentive.controller;

import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.service.TaskStockService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public boolean create(@RequestBody TaskStock stock){
        return taskStockService.save(stock);
    }

    @PutMapping("/update")
    public boolean update(@RequestBody TaskStock stock){
        return taskStockService.update(stock);
    }

    @DeleteMapping("/{taskId}")
    public boolean delete(@PathVariable Long taskId){
        return taskStockService.deleteById(taskId);
    }
}
