package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.UserBadge;
import com.whu.graduation.taskincentive.dao.mapper.UserBadgeMapper;
import com.whu.graduation.taskincentive.service.UserBadgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户徽章服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBadgeServiceImpl extends ServiceImpl<UserBadgeMapper, UserBadge>
        implements UserBadgeService {

    @Override
    public boolean save(UserBadge userBadge) {
        userBadge.setId(IdWorker.getId());
        return super.save(userBadge);
    }

    @Override
    public boolean update(UserBadge userBadge) {
        return super.updateById(userBadge);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public UserBadge getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<UserBadge> listAll() {
        return super.list();
    }

    @Override
    public List<UserBadge> listByUserId(Long userId) {
        return super.list(
                lambdaQuery().eq(UserBadge::getUserId, userId)
        );
    }
}