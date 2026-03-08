package com.whu.graduation.taskincentive.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
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
    public PageResult<TaskConfig> listAll(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) Integer status){
        if (status != null) {
            Page<TaskConfig> p = new Page<>(page, size);
            p = taskConfigService.selectByStatusPage(p, status);
            return PageResult.<TaskConfig>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
        }

        Page<TaskConfig> p = new Page<>(page, size);
        p = taskConfigService.selectPage(p);
        return PageResult.<TaskConfig>builder().total(p.getTotal()).page((int)p.getCurrent()).size((int)p.getSize()).items(p.getRecords()).build();
    }

    /** 根据ID查询 */
    @GetMapping("/{id}")
    public TaskConfig getById(@PathVariable Long id){
        return taskConfigService.getById(id);
    }

    /** 新增任务配置 */
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean create(@RequestBody TaskConfig taskConfig){
        return taskConfigService.save(taskConfig);
    }

    /** 更新任务配置 */
    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean update(@RequestBody TaskConfig taskConfig){
        return taskConfigService.update(taskConfig);
    }

    /** 删除任务配置 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public boolean delete(@PathVariable Long id){
        return taskConfigService.deleteById(id);
    }
}
