package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.graduation.taskincentive.dao.entity.TaskStock;
import com.whu.graduation.taskincentive.dao.mapper.TaskStockMapper;
import com.whu.graduation.taskincentive.service.TaskStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public List<TaskStock> getById(Long taskId) {
        QueryWrapper<TaskStock> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("task_id", taskId);
        return this.baseMapper.selectList(queryWrapper);
    }

    /**
     * 按任务ID和阶段序号获取库存记录
     * @param taskId 任务ID
     * @param stageIndex 阶梯任务阶段序号
     * @return 对应库存对象
     */
    @Override
    public TaskStock getByIdAndStageIndex(Long taskId, Integer stageIndex) {
        QueryWrapper<TaskStock> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("task_id", taskId).eq("stage_index", stageIndex);
        return this.baseMapper.selectOne(queryWrapper);
    }

    @Override
    public boolean deductStock(Long taskId, Integer stageIndex, Integer count) {
        int affected = this.baseMapper.deductStock(taskId, stageIndex, count);
        return affected > 0;
    }
}