package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.TaskConfig;
import com.whu.graduation.taskincentive.dao.mapper.TaskConfigMapper;
import com.whu.graduation.taskincentive.service.TaskConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskConfigServiceImpl extends ServiceImpl<TaskConfigMapper, TaskConfig>
        implements TaskConfigService {

    @Override
    public boolean save(TaskConfig taskConfig) {
        taskConfig.setId(IdWorker.getId());
        return super.save(taskConfig);
    }

    @Override
    public boolean update(TaskConfig taskConfig) {
        return super.updateById(taskConfig);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public TaskConfig getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<TaskConfig> listAll() {
        return super.list();
    }

    @Override
    public List<TaskConfig> selectByTaskName(String taskName) {
        return this.baseMapper.selectByTaskName(taskName);
    }

    @Override
    public Page<TaskConfig> selectByTaskTypePage(Page<TaskConfig> page, String taskType) {
        page.setRecords(this.baseMapper.selectByTaskTypePage(taskType, page));
        return page;
    }

    @Override
    public Page<TaskConfig> selectByStatusPage(Page<TaskConfig> page, Integer status) {
        page.setRecords(this.baseMapper.selectByStatusPage(status, page));
        return page;
    }
}