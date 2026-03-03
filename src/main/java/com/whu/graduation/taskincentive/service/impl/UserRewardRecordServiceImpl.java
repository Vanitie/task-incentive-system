package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.Badge;
import com.whu.graduation.taskincentive.dao.entity.UserBadge;
import com.whu.graduation.taskincentive.dao.entity.UserRewardRecord;
import com.whu.graduation.taskincentive.dao.mapper.BadgeMapper;
import com.whu.graduation.taskincentive.dao.mapper.UserRewardRecordMapper;
import com.whu.graduation.taskincentive.service.UserRewardRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户奖励记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRewardRecordServiceImpl extends ServiceImpl<UserRewardRecordMapper, UserRewardRecord>
        implements UserRewardRecordService {

    private final UserRewardRecordMapper userRewardRecordMapper;

    @Override
    public boolean save(UserRewardRecord record) {
        record.setId(IdWorker.getId());
        return super.save(record);
    }

    @Override
    public boolean update(UserRewardRecord record) {
        return super.updateById(record);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public UserRewardRecord getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<UserRewardRecord> listAll() {
        return super.list();
    }

    @Override
    public List<UserRewardRecord> selectByUserId(Long userId) {
        return userRewardRecordMapper.selectByUserId(userId);
    }

    @Override
    public List<UserRewardRecord> selectUnclaimedPhysicalReward(Long userId) {
        return userRewardRecordMapper.selectUnclaimedPhysicalRewards(userId);
    }

    @Override
    public List<UserRewardRecord> selectByStatus(Long userId, Integer status) {
        return super.list(lambdaQuery()
                .eq(UserRewardRecord::getUserId, userId)
                .eq(UserRewardRecord::getStatus, status)
                .orderByAsc(UserRewardRecord::getCreateTime)
        );
    }
}