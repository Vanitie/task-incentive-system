package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.UserActionLog;
import com.whu.graduation.taskincentive.dao.mapper.UserActionLogMapper;
import com.whu.graduation.taskincentive.service.UserActionLogService;
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
}