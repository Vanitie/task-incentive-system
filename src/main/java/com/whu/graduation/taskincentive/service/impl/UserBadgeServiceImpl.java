package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.Badge;
import com.whu.graduation.taskincentive.dao.entity.UserBadge;
import com.whu.graduation.taskincentive.dao.mapper.BadgeMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserBadgeMapper;
import com.whu.graduation.taskincentive.service.UserBadgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 用户徽章服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBadgeServiceImpl extends ServiceImpl<UserBadgeMapper, UserBadge>
        implements UserBadgeService {

    private final UserBadgeMapper userBadgeMapper;

    private final BadgeMapper badgeMapper;

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

    @Override
    public Page<UserBadge> selectByUserIdPage(Page<UserBadge> page, Long userId) {
        QueryWrapper<UserBadge> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("acquire_time");
        return this.baseMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional
    public boolean grantBadge(Long userId, Integer badgeCode) {

        // 1 查询徽章
        Badge badge = badgeMapper.selectById(badgeCode);

        if (badge == null) {
            log.error("徽章不存在 code={}", badgeCode);
            return false;
        }

        Long badgeId = badge.getId();

        // 2 幂等检查（防重复发）
        UserBadge exist = userBadgeMapper.selectUserBadge(userId, badgeId);

        if (exist != null) {
            log.info("用户已拥有该徽章 userId={} badgeId={}",
                    userId, badgeId);
            return true;
        }

        // 3 创建记录
        UserBadge userBadge = new UserBadge();

        userBadge.setId(IdWorker.getId());
        userBadge.setUserId(userId);
        userBadge.setBadgeId(badgeId);
        userBadge.setAcquireTime(new Date());

        userBadgeMapper.insert(userBadge);

        return true;
    }
}