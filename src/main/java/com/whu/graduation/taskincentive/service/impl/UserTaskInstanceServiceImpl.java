package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.UserTaskInstance;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskInstanceMapper;
import com.whu.graduation.taskincentive.service.UserTaskInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户任务进度服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserTaskInstanceServiceImpl extends ServiceImpl<UserTaskInstanceMapper, UserTaskInstance>
        implements UserTaskInstanceService {

    private final UserTaskInstanceMapper userTaskInstanceMapper;

    @Override
    public boolean save(UserTaskInstance progress) {
        progress.setId(IdWorker.getId());
        return super.save(progress);
    }

    @Override
    public boolean update(UserTaskInstance progress) {
        return super.updateById(progress);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public UserTaskInstance getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<UserTaskInstance> listAll() {
        return super.list();
    }

    @Override
    public List<UserTaskInstance> selectByUserId(Long userId) {
        return userTaskInstanceMapper.selectByUserId(userId);
    }

    @Override
    public int updateWithVersion(UserTaskInstance instance) {
        return userTaskInstanceMapper.updateWithVersion(instance);
    }

    @Override
    public UserTaskInstance getOrCreate(Long userId, Long taskId) {

        UserTaskInstance instance =
                baseMapper.selectByUserAndTask(userId, taskId);

        if (instance != null) {
            return instance;
        }

        instance = new UserTaskInstance();
        instance.setId(IdWorker.getId());
        instance.setUserId(userId);
        instance.setTaskId(taskId);
        instance.setProgress(0);
        instance.setStatus(0);
        instance.setVersion(0);

        try {
            baseMapper.insert(instance);
        } catch (Exception e) {
            // 说明已被其他线程创建
            instance = baseMapper.selectByUserAndTask(userId, taskId);
        }

        return instance;
    }
}