package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.UserActionLog;
import com.whu.graduation.taskincentive.dao.mapper.UserActionLogMapper;
import com.whu.graduation.taskincentive.service.UserActionLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户行为日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserActionLogServiceImpl extends ServiceImpl<UserActionLogMapper, UserActionLog>
        implements UserActionLogService {

    private final UserActionLogMapper userActionLogMapper;

    @Override
    public boolean save(UserActionLog log) {
        log.setId(IdWorker.getId());
        return super.save(log);
    }

    @Override
    public boolean update(UserActionLog log) {
        return super.updateById(log);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public UserActionLog getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<UserActionLog> listAll() {
        return super.list();
    }

    @Override
    public List<UserActionLog> selectByUserId(Long userId) {
        return this.baseMapper.selectByUserId(userId);
    }

    @Override
    public List<UserActionLog> selectByActionType(String actionType) {
        return this.baseMapper.selectByActionType(actionType);
    }

    @Override
    public Page<UserActionLog> selectByUserIdPage(com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserActionLog> page, Long userId) {
        QueryWrapper<UserActionLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("create_time");
        return this.baseMapper.selectPage(page, wrapper);
    }

    @Override
    public Page<UserActionLog> selectByActionTypePage(com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserActionLog> page, String actionType) {
        QueryWrapper<UserActionLog> wrapper = new QueryWrapper<>();
        wrapper.eq("action_type", actionType).orderByDesc("create_time");
        return this.baseMapper.selectPage(page, wrapper);
    }

    @Override
    public Long countUserAction(Long userId, String actionType) {
        return super.count(lambdaQuery()
                .eq(UserActionLog::getUserId,userId)
                .eq(UserActionLog::getActionType,actionType)
        );
    }

    @Override
    public Page<UserActionLog> queryByConditions(Page<UserActionLog> page, Long userId, String actionType, String startTime, String endTime) {
        QueryWrapper<UserActionLog> wrapper = new QueryWrapper<>();
        if (userId != null) wrapper.eq("user_id", userId);
        if (actionType != null && !actionType.isEmpty()) wrapper.eq("action_type", actionType);
        if (startTime != null && !startTime.isEmpty()) wrapper.ge("create_time", startTime);
        if (endTime != null && !endTime.isEmpty()) wrapper.le("create_time", endTime);
        wrapper.orderByDesc("create_time");
        return this.baseMapper.selectPage(page, wrapper);
    }
}