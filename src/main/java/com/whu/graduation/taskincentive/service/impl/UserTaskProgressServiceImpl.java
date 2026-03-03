package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.UserTaskProgress;
import com.whu.graduation.taskincentive.dao.mapper.UserTaskProgressMapper;
import com.whu.graduation.taskincentive.service.UserTaskProgressService;
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
public class UserTaskProgressServiceImpl extends ServiceImpl<UserTaskProgressMapper, UserTaskProgress>
        implements UserTaskProgressService {

    @Override
    public boolean save(UserTaskProgress progress) {
        progress.setId(IdWorker.getId());
        return super.save(progress);
    }

    @Override
    public boolean update(UserTaskProgress progress) {
        return super.updateById(progress);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public UserTaskProgress getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<UserTaskProgress> listAll() {
        return super.list();
    }

    @Override
    public List<UserTaskProgress> selectByUserId(Long userId) {
        return this.baseMapper.selectByUserId(userId);
    }
}