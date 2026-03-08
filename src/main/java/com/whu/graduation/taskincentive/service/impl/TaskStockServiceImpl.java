package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.dao.mapper.TaskStockMapper;
import com.whu.graduation.taskincentive.service.TaskStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 任务库存服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStockServiceImpl extends ServiceImpl<TaskStockMapper, TaskStock>
        implements TaskStockService {

    @Override
    public boolean save(TaskStock taskStock) {
        return super.save(taskStock);
    }

    @Override
    public boolean update(TaskStock taskStock) {
        return super.updateById(taskStock);
    }

    @Override
    public boolean deleteById(Long taskId) {
        return super.removeById(taskId);
    }

    @Override
    public TaskStock getById(Long taskId) {
        return super.getById(taskId);
    }

    @Override
    public boolean deductStock(Long taskId, Integer count) {
        int affected = this.baseMapper.deductStock(taskId, count);
        return affected > 0;
    }
}